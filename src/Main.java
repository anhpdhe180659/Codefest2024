import io.socket.emitter.Emitter;
import jsclub.codefest2024.sdk.Hero;
import jsclub.codefest2024.sdk.algorithm.PathUtils;
import jsclub.codefest2024.sdk.base.Node;
import jsclub.codefest2024.sdk.model.ElementType;
import jsclub.codefest2024.sdk.model.GameMap;
import jsclub.codefest2024.sdk.model.equipments.Armor;
import jsclub.codefest2024.sdk.model.equipments.HealingItem;
import jsclub.codefest2024.sdk.model.obstacles.Obstacle;
import jsclub.codefest2024.sdk.model.players.Player;
import jsclub.codefest2024.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    // Tạo danh sách để lưu trữ tối đa 2 khẩu súng
    private static final LinkedList<Weapon> playerWeapons = new LinkedList<>();
    private static final String SERVER_URL = "https://cf-server.jsclub.dev";
    //TODO:gameid
    private static final String GAME_ID = "155847";
    private static final String PLAYER_NAME = "test-chucvodich";
    private static final String PLAYER_KEY = "hello";


    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, PLAYER_KEY);
        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                try {
                    GameMap gameMap = hero.getGameMap();
                    gameMap.updateOnUpdateMap(args[0]);
                    Player player = gameMap.getCurrentPlayer();
                    List<Player> otherPlayers = gameMap.getOtherPlayerInfo();
                    List<Obstacle> restricedList = gameMap.getListIndestructibleObstacles();
                    List<HealingItem> healingItemList = gameMap.getListHealingItems();
                    List<Armor> armorsList = gameMap.getListArmors();
                    restricedList.addAll(gameMap.getListTraps());
//                    restricedList.addAll(gameMap.getListChests());
                    Node currentNode = new Node(player.getX(), player.getY());
                    List<Node> restrictedNodes = new ArrayList<>();
                    List<Node> otherPlayersNode = new ArrayList<>();
                    List<Node> healingItemNode = new ArrayList<>();
                    List<Node> chestNode = new ArrayList<>();
                    List<Weapon> throwableList = gameMap.getAllThrowable();
                    List<Weapon> meleeList = gameMap.getAllMelee();
                    List<Obstacle> listChest = gameMap.getListChests();
                    // Lấy vị trí của người chơi khác
                    for (Player otherPlayer : otherPlayers) {
                        if (otherPlayer.getIsAlive()) {
                            otherPlayersNode.add(new Node(otherPlayer.getX(), otherPlayer.getY()));
                        }
                    }
                    for (Obstacle chest : listChest) {
                        chestNode.add(new Node(chest.getX(), chest.getY()));
                    }
                    // Lấy vị trí của các vật phẩm hồi phục
                    for (HealingItem healingItem : healingItemList) {
                        healingItemNode.add(new Node(healingItem.getX(), healingItem.getY()));
                    }

                    for (Obstacle obstacle : restricedList) {
                        restrictedNodes.add(new Node(obstacle.getX(), obstacle.getY()));
                    }

                    // Lấy danh sách súng của người chơi
                    Weapon isUseGun = hero.getInventory().getGun();
                    Weapon isUseMelee = hero.getInventory().getMelee();
                    Weapon isUseThrow = hero.getInventory().getThrowable();
                    boolean pickedUpWeapon = (isUseGun != null) || (isUseThrow != null) || (!isUseMelee.getId().equals("HAND"));
                    // Nếu chưa co súng, tìm và nhặt thêm
                    if (!pickedUpWeapon) {
                        Node nearestMelee = findNearestNode(currentNode, meleeList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                        Node nearestThrowable = findNearestNode(currentNode, throwableList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                        System.out.println("chua co gi di nhat sung");
                        List<Weapon> gunList = gameMap.getAllGun();
                        List<Node> gunNode = gunList.stream()
                                .map(node -> new Node(node.getX(), node.getY()))
                                .collect(Collectors.toList());
                        Node nearestGun = findNearestNode(currentNode, gunNode, gameMap);
                        System.out.println("nearest gun : " + nearestGun.getX() + "/" + nearestGun.getY());

                        if (nearestGun != null) {
                            pickUpItem(currentNode, nearestGun, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                        } else if (nearestMelee != null && hero.getInventory().getMelee().equals("HAND")) {
                            System.out.println("Bo m nhat melee");
                            System.out.println(hero.getInventory().getMelee());
                            pickUpItem(currentNode, nearestMelee, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                        } else if (nearestThrowable != null && hero.getInventory().getThrowable() == null) {
                            System.out.println("Bo m nhat throw");
                            System.out.println(hero.getInventory().getThrowable());
                            pickUpItem(currentNode, nearestThrowable, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                        }
                    } else {
                        System.out.println("co sung r di ban cai gi nao");
                        //check khoang cach dich va chest
                        // tim chest gan nhat
                        Node nearestChest = findNearestNode(currentNode, chestNode, gameMap);
                        System.out.println("Nearesr chest 1   " + nearestChest.getX() + "/" + nearestChest.getY());
                        // tim dich gan nhat
                        Node nearestEnemies = findNearestNode(currentNode, otherPlayersNode, gameMap);
                        //tinh khoang cach
                        if (nearestChest != null && nearestEnemies != null) {
                            double distanceFromchest = distanceBetween2Nodes(currentNode, nearestChest);
                            double distanceFromEnimy = distanceBetween2Nodes(currentNode, nearestEnemies);
                            List<Node> allEnemies = new ArrayList<>();
                            allEnemies.add(nearestEnemies);
                            if (detectEnemies(currentNode, allEnemies)) {
                                System.out.println("Gan dich hon ban dich di");
                                // Nếu máu của người chơi dưới 50, check mau dich sap het chua
                                if (player.getHp() <= 50) {
                                    System.out.println("Mau hien tai : " + player.getHp());
                                    if (!hero.getInventory().getListHealingItem().isEmpty()) {
                                        hero.useItem(hero.getInventory().getListHealingItem().get(0).getId());
                                    }
                                    Player nearestEnimy = findNearestPlayer(currentNode, otherPlayers);
                                    System.out.println("Dich gan nhat la " + nearestEnimy.getX() + "/" + nearestEnimy.getY());
                                    System.out.println("Khoang cach toi dich la " + distanceFromEnimy);
                                    if (nearestEnimy.getHp() <= player.getHp()) {
                                        System.out.println("Dich hp: " + nearestEnimy.getHp());
                                        System.out.println("dich mau yeu hon ban dich thoi");
                                        attackEnemies(currentNode, nearestEnemies, restrictedNodes, otherPlayersNode, chestNode, gameMap, hero);
                                    } else {
                                        nearestChest = findNearestNode(currentNode, listChest.stream().map((node) -> new Node(node.getX(), node.getY())).collect(Collectors.toList()), gameMap);
                                        if (nearestChest != null) {
                                            System.out.println("Nearesr chest 2   " + nearestChest.getX() + "/" + nearestChest.getY());
                                            openChest(currentNode, nearestChest, hero, gameMap, restrictedNodes, otherPlayersNode);
                                            System.out.println("Bo di nhat chest o dong 131");
                                        }
                                        Node nearestHealingItem = findNearestNode(currentNode, healingItemNode, gameMap);
                                        Node nearestArmor = findNearestNode(currentNode, armorsList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                                        Node nearestMelee = findNearestNode(currentNode, meleeList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                                        Node nearestThrowable = findNearestNode(currentNode, throwableList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);

                                        if (nearestHealingItem != null && (hero.getInventory().getListHealingItem().size() < 4)) {
                                            System.out.println("Bo nhat healing o dong 139");
                                            pickUpItem(currentNode, nearestHealingItem, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                        }
                                        if (nearestArmor != null && (hero.getInventory().getListArmor().size() < 2)) {
                                            System.out.println("Bo nhat armor o dong 143");
                                            pickUpItem(currentNode, nearestArmor, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                        }
                                        if (nearestMelee != null && hero.getInventory().getMelee().getId().equals("HAND")) {
                                            System.out.println("bo nhat melee o dong 147");
                                            System.out.println(hero.getInventory().getMelee());
                                            pickUpItem(currentNode, nearestMelee, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                        }
                                        if (nearestThrowable != null && hero.getInventory().getThrowable() == null) {
                                            System.out.println("Bo nhat throw o dong 152");
                                            System.out.println(hero.getInventory().getThrowable());
                                            pickUpItem(currentNode, nearestThrowable, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                        }
                                    }
                                } else {
                                    System.out.println("Mau nhieu vl ban dich di: " + player.getHp());
                                    System.out.println("DIch dang o :" + nearestEnemies.getX() + "/" + nearestEnemies.getY());
                                    System.out.println("Tan cong dich o dong 160");
                                    attackEnemies(currentNode, nearestEnemies, restrictedNodes, otherPlayersNode, chestNode, gameMap, hero);
                                }
                            } else {
                                System.out.println("Dich xa qua nhat chest cai.");
                                if (checkFull(hero)) {
                                    System.out.println("nma thoi du do roi chien me no di !!!");
                                    System.out.println("tan cong dich o dong 168");
                                    nearestEnemies = findNearestNode(currentNode, otherPlayersNode, gameMap);
                                    if (nearestEnemies != null) {
                                        System.out.println("dm m toi so roi m o : " + nearestEnemies.getX() + "/" + nearestEnemies.getY());
                                        attackEnemies(currentNode, nearestEnemies, restrictedNodes, otherPlayersNode, chestNode, gameMap, hero);
                                    } else {
                                        System.out.println("Dm het cu dich roi thoi di dap da z");
                                        nearestChest = findNearestNode(currentNode, chestNode, gameMap);
                                        Node nearestHealingItem = findNearestNode(currentNode, healingItemNode, gameMap);
                                        Node nearestArmor = findNearestNode(currentNode, armorsList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                                        Node nearestMelee = findNearestNode(currentNode, meleeList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                                        Node nearestThrowable = findNearestNode(currentNode, throwableList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                                        List<Node> itemsNode = new ArrayList<>();
                                        if (nearestArmor != null && hero.getInventory().getListArmor().size() < 2) {
                                            itemsNode.add(nearestArmor);
                                        }

                                        if (nearestMelee != null && hero.getInventory().getMelee().getId().equals("HAND")) {
                                            itemsNode.add(nearestMelee);
                                        }

                                        if (nearestThrowable != null && hero.getInventory().getThrowable() == null) {
                                            itemsNode.add(nearestThrowable);
                                        }

                                        if (nearestHealingItem != null && hero.getInventory().getListHealingItem().size() < 4) {
                                            itemsNode.add(nearestHealingItem);
                                        }
                                        if (nearestChest != null) {
                                            itemsNode.add(nearestChest);
                                        }
                                        Node nearestItem = findNearestNode(currentNode, itemsNode, gameMap);
                                        if (chestNode.contains(nearestItem)) {
                                            System.out.println("Nhat ruong gan nhat");
                                            openChest(currentNode, nearestItem, hero, gameMap, restrictedNodes, otherPlayersNode);
                                        } else {
                                            System.out.println("Nhat do gan nhat");
                                            pickUpItem(currentNode, nearestItem, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                        }
                                    }
                                } else {
                                    System.out.println("Chua du do di dap chest cai");
                                    nearestChest = findNearestNode(currentNode, chestNode, gameMap);
                                    if (nearestChest != null) {
                                        System.out.println("Nearesr chest 4   " + nearestChest.getX() + "/" + nearestChest.getY());
                                        System.out.println("Tan cong dich o dong 175");
                                        openChest(currentNode, nearestChest, hero, gameMap, restrictedNodes, otherPlayersNode);
                                    }

                                    Node nearestHealingItem = findNearestNode(currentNode, healingItemNode, gameMap);
                                    Node nearestArmor = findNearestNode(currentNode, armorsList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                                    Node nearestMelee = findNearestNode(currentNode, meleeList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                                    Node nearestThrowable = findNearestNode(currentNode, throwableList.stream().map((node) -> new Node(node.getX(), node.getY())).toList(), gameMap);
                                    if (nearestHealingItem != null && (hero.getInventory().getListHealingItem().size() < 4)) {
                                        pickUpItem(currentNode, nearestHealingItem, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                    }
                                    if (nearestArmor != null && (hero.getInventory().getListArmor().size() < 2)) {
                                        pickUpItem(currentNode, nearestArmor, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                    }
                                    if (!hero.getInventory().getListHealingItem().isEmpty() && player.getHp() <= 50) {
                                        hero.useItem(hero.getInventory().getListHealingItem().get(0).getId());
                                    }
                                    if (nearestMelee != null && hero.getInventory().getMelee().getId().equals("HAND")) {
                                        System.out.println(hero.getInventory().getMelee());
                                        pickUpItem(currentNode, nearestMelee, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                    }
                                    if (nearestThrowable != null && hero.getInventory().getThrowable() == null) {
                                        System.out.println(hero.getInventory().getThrowable());
                                        pickUpItem(currentNode, nearestThrowable, hero, gameMap, restrictedNodes, chestNode, otherPlayersNode);
                                    }
                                }
                            }
                        } else if (nearestChest == null) {
                            attackEnemies(currentNode, nearestEnemies, restrictedNodes, otherPlayersNode, chestNode, gameMap, hero);
                        } else if (nearestEnemies == null) {
                            openChest(currentNode, nearestEnemies, hero, gameMap, restrictedNodes, otherPlayersNode);
                        } else {
                            hero.move("uurrddll");
                        }
                    }

                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

    public static boolean detectEnemies(Node currentNode, List<Node> enemyNodes) throws IOException {
        int detectionRange = 10;
        if (enemyNodes.isEmpty()) return false;
        for (Node enemyNode : enemyNodes) {
            int disX = Math.abs(currentNode.getX() - enemyNode.getX());
            int disY = Math.abs(currentNode.getY() - enemyNode.getY());


            if (disX <= detectionRange && disY <= detectionRange) {
                System.out.println("Phat hien dich, tan cong ngay!!");
                return true;
            }
        }
        return false;
    }

    public static Player findNearestPlayer(Node currentNode, List<Player> playerList) {
        if (playerList.isEmpty()) return null;
        double min = Double.MAX_VALUE;
        Player nearestNode = null;
        for (Player node : playerList) {
            if (node.getIsAlive()) {
                double distance = Math.sqrt(Math.pow(currentNode.getX() - node.getX(), 2)
                        + Math.pow(currentNode.getY() - node.getY(), 2));
                if (distance < min) {
                    min = distance;
                    nearestNode = node;
                }
            }
        }
        return nearestNode;
    }

    public static boolean checkFull(Hero hero) {
        Weapon isUseGun = hero.getInventory().getGun();
        Weapon isUseMelee = hero.getInventory().getMelee();
        Weapon isUseThrow = hero.getInventory().getThrowable();
        boolean pickedUpWeapon = (isUseGun != null) || (isUseThrow != null) && (!isUseMelee.getId().equals("HAND"));
        boolean fullArmorAndHealing = hero.getInventory().getListArmor().size() >= 1 && hero.getInventory().getListHealingItem().size() >= 2;
        return pickedUpWeapon && fullArmorAndHealing;
    }

    public static void openChest(Node currentNode, Node enemieNode, Hero hero, GameMap gameMap, List<Node> restrictedNodes, List<Node> otherPlayerNodes) throws IOException {
        Weapon isUseGun = hero.getInventory().getGun();
        Weapon isUseMelee = hero.getInventory().getMelee();
        Weapon isUseThrow = hero.getInventory().getThrowable();
        Weapon weapon = null;
        int attackRange = 0;
        int disY = currentNode.getY() - enemieNode.getY();
        int disX = currentNode.getX() - enemieNode.getX();
        System.out.println("Chest position : " + enemieNode.getX() + "/" + enemieNode.getY());
        System.out.println("Me : " + currentNode.getX() + "/" + currentNode.getY());
        restrictedNodes.addAll(otherPlayerNodes);
        if ((disX == 0 && Math.abs(disY) >= 3) || (disY == 0 && Math.abs(disX) >= 3)) {
            System.out.println("Xa qua doi vu khi cai");
            if (isUseGun != null) {
                weapon = isUseGun;
            } else if (isUseThrow != null) {
                weapon = isUseThrow;
            } else if (!isUseMelee.getId().equals("HAND")) {
                weapon = isUseMelee;
            }
            attackRange = weapon.getRange();
        }
        if ((disX == 0 && Math.abs(disY) < 3) || (disY == 0 && Math.abs(disX) < 3)) {
            System.out.println("Gan qua doi vu khi cai");
            if (!isUseMelee.getId().equals("HAND")) {
                weapon = isUseMelee;
            } else if (isUseGun != null) {
                weapon = isUseGun;
            } else if (isUseThrow != null) {
                weapon = isUseThrow;
            }
            attackRange = weapon.getRange();
        }

        System.out.println("Vu khi dang dung " + weapon);
        if (disX == 0) {
            if (weapon.getType().equals(ElementType.THROWABLE)) {
                if (disY > 0 && disY >= attackRange - 3 && disY <= attackRange + 3) {
                    System.out.println("ban sang duoi dgiet dich nay");
                    hero.throwItem("d");
                } else if (disY < 0 && disY <= -(attackRange - 3) && disY >= -(attackRange + 3)) {
                    System.out.println("ban sang tren dgiet dich nay");
                    hero.throwItem("u");
                }
            }
            if (disY > 0 && disY <= attackRange) {
                System.out.println("ban sang duoi giet dich nay");
                if (weapon.getType().equals(ElementType.GUN))
                    hero.shoot("d");
                if (weapon.getType().equals(ElementType.MELEE))
                    hero.attack("d");
            } else if (disY < 0 && disY >= -attackRange) {
                System.out.println("ban sang tren degiet dich nay");
                if (weapon.getType().equals(ElementType.GUN))
                    hero.shoot("u");
                if (weapon.getType().equals(ElementType.MELEE))
                    hero.attack("u");
            }

        } else if (disY == 0) {
            if (weapon.getType().equals(ElementType.THROWABLE)) {
                if (disX > 0 && disX >= attackRange - 3 && disX <= attackRange + 3) {
                    System.out.println("ban sang duoi giet dicht nay");
                    hero.throwItem("l");
                } else if (disX < 0 && disX <= -(attackRange - 3) && disX >= -(attackRange + 3)) {
                    System.out.println("ban sang tren dgiet dich nay");
                    hero.throwItem("r");
                }
            }
            if (disX > 0 && disX <= attackRange) {
                System.out.println("ban sang trai giet dich nay");
                if (weapon.getType().equals(ElementType.GUN))
                    hero.shoot("l");
                if (weapon.getType().equals(ElementType.MELEE))
                    hero.attack("l");
            } else if (disX < 0 && disX >= -attackRange) {
                System.out.println("ban sang phai giet dicht nay");
                if (weapon.getType().equals(ElementType.GUN))
                    hero.shoot("r");
                if (weapon.getType().equals(ElementType.MELEE))
                    hero.attack("r");
            }
        } else {
            System.out.print("Di chuyen den ruong :");
            System.out.println(PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, enemieNode, false));
            hero.move(PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, enemieNode, false));
        }
    }


    //tim node gan nhat
    public static Node findNearestNode(Node currentNode, List<Node> nodeList, GameMap gameMap) {
        if (nodeList.isEmpty()) return null;
        double min = Double.MAX_VALUE;
        Node nearestNode = null;
        for (Node node : nodeList) {
            if (PathUtils.checkInsideSafeArea(node, gameMap.getDarkAreaSize(), gameMap.getMapSize())) {
                double distance = Math.sqrt(Math.pow(currentNode.getX() - node.getX(), 2)
                        + Math.pow(currentNode.getY() - node.getY(), 2));
                if (distance < min) {
                    min = distance;
                    nearestNode = node;
                }
            }
        }
        return nearestNode;
    }

    public static void pickUpItem(Node currentNode, Node target, Hero hero, GameMap gameMap, List<Node> restrictedNodes, List<Node> chestNode, List<Node> otherPlayersNode) throws IOException {
        restrictedNodes.addAll(chestNode);
        if (currentNode.getX() == target.getX() && currentNode.getY() == target.getY()) {
            hero.pickupItem();
        } else {
            // cho vi tri cua tat ca ng choi vao vat han che
            restrictedNodes.addAll(otherPlayersNode);
// tim duong den item gan nhat
            hero.move(PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, target, false));
        }
    }

    public static void attackEnemies(Node currentNode, Node enemieNode, List<Node> restrictedNodes, List<Node> othersPlayerNodes, List<Node> chestNode, GameMap gameMap, Hero hero) throws IOException {
        Weapon isUseGun = hero.getInventory().getGun();
        Weapon isUseMelee = hero.getInventory().getMelee();
        Weapon isUseThrow = hero.getInventory().getThrowable();
        Weapon weapon = null;
        int attackRange = 0;
        int disY = currentNode.getY() - enemieNode.getY();
        int disX = currentNode.getX() - enemieNode.getX();
        restrictedNodes.addAll(othersPlayerNodes);
        restrictedNodes.addAll(chestNode);
        if ((disX == 0 && Math.abs(disY) >= 3) || (disY == 0 && Math.abs(disX) >= 3)) {
            System.out.println("Xa qua doi vu khi cai");
            if (isUseGun != null) {
                weapon = isUseGun;
            } else if (isUseThrow != null) {
                weapon = isUseThrow;
            } else if (!isUseMelee.getId().equals("HAND")) {
                weapon = isUseMelee;
            }
            attackRange = weapon.getRange();
        }
        if ((disX == 0 && Math.abs(disY) < 3) || (disY == 0 && Math.abs(disX) < 3)) {
            System.out.println("Gan qua doi vu khi cai");
            if (!isUseMelee.getId().equals("HAND")) {
                weapon = isUseMelee;
            } else if (isUseGun != null) {
                weapon = isUseGun;
            } else if (isUseThrow != null) {
                weapon = isUseThrow;
            }
            attackRange = weapon.getRange();
        }

        System.out.println("Vu khi dang dung " + weapon);
        if (disX == 0) {
            if (weapon.getType().equals(ElementType.THROWABLE)) {
                if (disY > 0 && disY >= attackRange - 3 && disY <= attackRange + 3) {
                    System.out.println("ban sang duoi dgiet dich nay");
                    hero.throwItem("d");
                } else if (disY < 0 && disY <= -(attackRange - 3) && disY >= -(attackRange + 3)) {
                    System.out.println("ban sang tren dgiet dich nay");
                    hero.throwItem("u");
                }
            }
            if (disY > 0 && disY <= attackRange) {
                System.out.println("ban sang duoi giet dich nay");
                if (weapon.getType().equals(ElementType.GUN))
                    hero.shoot("d");
                if (weapon.getType().equals(ElementType.MELEE))
                    hero.attack("d");
            } else if (disY < 0 && disY >= -attackRange) {
                System.out.println("ban sang tren degiet dich nay");
                if (weapon.getType().equals(ElementType.GUN))
                    hero.shoot("u");
                if (weapon.getType().equals(ElementType.MELEE))
                    hero.attack("u");
            }

        } else if (disY == 0) {
            if (weapon.getType().equals(ElementType.THROWABLE)) {
                if (disX > 0 && disX >= attackRange - 3 && disX <= attackRange + 3) {
                    System.out.println("ban sang duoi giet dicht nay");
                    hero.throwItem("l");
                } else if (disX < 0 && disX <= -(attackRange - 3) && disX >= -(attackRange + 3)) {
                    System.out.println("ban sang tren dgiet dich nay");
                    hero.throwItem("r");
                }
            }
            if (disX > 0 && disX <= attackRange) {
                System.out.println("ban sang trai giet dich nay");
                if (weapon.getType().equals(ElementType.GUN))
                    hero.shoot("l");
                if (weapon.getType().equals(ElementType.MELEE))
                    hero.attack("l");
            } else if (disX < 0 && disX >= -attackRange) {
                System.out.println("ban sang phai giet dicht nay");
                if (weapon.getType().equals(ElementType.GUN))
                    hero.shoot("r");
                if (weapon.getType().equals(ElementType.MELEE))
                    hero.attack("r");
            }

        } else {
            Node target = new Node(enemieNode.getX(), enemieNode.getY());

            System.out.println("Duong toi thang dich : "+PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, target, false));
            hero.move(PathUtils.getShortestPath(gameMap, restrictedNodes, currentNode, target, false));
        }


    }

    public static double distanceBetween2Nodes(Node a, Node b) {
        if (b == null) {
            return Double.MAX_VALUE;
        }
        System.out.println("target position " + b.getX() + "/" + b.getY());
        return Math.sqrt(Math.pow((a.getX() - b.getX()), 2) + Math.pow((a.getY() - b.getY()), 2));
    }

    public static void attackWithThrowable(Hero hero, double dis, Node enemy, Node currentNode) throws IOException {
        int xC = currentNode.getX();
        int yC = currentNode.getY();
        int xE = enemy.x;
        int yE = enemy.getY();
        if (dis >= 3 && dis <= Math.sqrt(3 * 3 + 9 * 9)) {
            if (xE >= xC - 3 && xE <= xC + 3) {
                if (yE >= yC + 3 && yE <= yC + 9) {
                    hero.throwItem("u");
                } else if (yE >= yC - 9 && yE <= yC - 3) {
                    hero.throwItem("d");
                }
            }
            if (yE >= yC - 3 && yE <= yC + 3) {
                if (xE >= xC + 3 && xE <= xC + 9) {
                    hero.throwItem("r");
                } else if (xE >= xC - 9 && xE <= xC - 3) {
                    hero.throwItem("l");
                }
            }
        }
    }

}