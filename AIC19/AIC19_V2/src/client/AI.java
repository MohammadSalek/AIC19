package client;

import client.model.*;
import client.model.Map;
import javafx.print.Collation;

import java.lang.reflect.Array;
import java.util.*;

public class AI {

    private Map map;
    private Cell[] objCells;
    private int movePhases = 6;
    private HashMap<Integer, Boolean> isStayingThisPhase = new HashMap<>();
    private HashMap<Integer, Boolean> needsHealing = new HashMap<>();
    private HashMap<Integer, Boolean> isInFirstLayout = new HashMap<>();
    private HashMap<Integer, Boolean> enemyDying = new HashMap<>();

    private Cell healerPlace = null;
    private Cell[] blasterPlaces = new Cell[3];
    private boolean moveInit = true;
    private Cell[] firstDodgeCells;

    private int healerID, blasterID0, blasterID1, blasterID2;
    private int minHeroDist = 3;
    private boolean shouldBlastersMove = false;



    //alarms:
    private int blasterDodgeAlarm = 20;
    private int blasterHealAlarm = 95;
    private int healerDodgeAlarm = 30;
    private int healerHealAlarm = 30;

    //damages:
    private int damageBlasterAttack = 20;
    private int damageBlasterBomb = 40;
    private int damageHealerAttack = 25;

    //healer:
    private int healerAttackRange = 4;
    private int healingRange = 4;
    private int healerDodgeRange = 4;

    //blaster:
    private int blasterAttackRange = 4;
    private int blasterAttackEffect = 1;
    private int blasterBombAttackRange = 5;
    private int bombEffectRange = 2;
    private int blasterDodgeRange = 4;



    public void preProcess(World world) {
        System.out.println("<-- pre process -->");
        healerID = blasterID0 = blasterID1 = blasterID2 = -1;
        map = world.getMap();
        objCells = map.getObjectiveZone();

        //healerPlace:
        healerPlace = getClosestObjCell(world, map.getMyRespawnZone()[0], new HashSet<>());

        //blaster2 & blaster3:
        Cell[] otherBlasterCells = findBlasterCells(world);
        blasterPlaces[0] = otherBlasterCells[0];
        blasterPlaces[1] = otherBlasterCells[1];
        blasterPlaces[2] = otherBlasterCells[2];
    }

    public void pickTurn(World world) {
        System.out.println("<-- pick -->");
        if (world.getCurrentTurn() == 0) {
            world.pickHero(HeroName.HEALER);
        } else if (world.getCurrentTurn() == 1) {
            world.pickHero(HeroName.BLASTER);
        } else if (world.getCurrentTurn() == 2) {
            world.pickHero(HeroName.BLASTER);
        } else if (world.getCurrentTurn() == 3) {
            world.pickHero(HeroName.BLASTER);
        }
    }

    public void moveTurn(World world) {

        System.out.println("\n<-- move -->");
        Hero[] myHeroes = world.getMyHeroes();

        /* < INIT > */
        if (moveInit) {
            //isStaying and needsHealing and heroesID:
            for (Hero myHero : world.getMyHeroes()) {
                isStayingThisPhase.put(myHero.getId(), true);
                needsHealing.put(myHero.getId(), false);
                isInFirstLayout.put(myHero.getId(), false);

                if (myHero.getName().equals(HeroName.HEALER)) {
                    healerID = myHero.getId();
                }
                else if (myHero.getName().equals(HeroName.BLASTER)) {
                    if (blasterID0 == -1) {
                        blasterID0 = myHero.getId();
                    }
                    else if (blasterID1 == -1) {
                        blasterID1 = myHero.getId();
                    }
                    else if (blasterID2 == -1) {
                        blasterID2 = myHero.getId();
                    }
                }
            }

            //first dodge:
            firstDodgeCells = findFirstDodgeCells(world, myHeroes);

            moveInit = false;
        }
        /* < / INIT > */

        /* < 0. FIRST MOVE PHASE > */
        if (world.getMovePhaseNum() == 0) {
            for (Hero myHero : myHeroes) {
                if ((myHero.getRemRespawnTime() == 0) &&
                        ((myHero.getName().equals(HeroName.HEALER) && (getHPPercentage(myHero) <= healerHealAlarm))
                                || (myHero.getName().equals(HeroName.BLASTER) && (getHPPercentage(myHero) <= blasterHealAlarm))
                        )) {
                    needsHealing.put(myHero.getId(), true);
                } else {
                    needsHealing.put(myHero.getId(), false);
                }
            }

            for (Hero enemy : world.getOppHeroes()) {
                enemyDying.put(enemy.getId(), false);
            }

        }
        /* < / 0. FIRST MOVE PHASE > */

        /* < 1. FIND DESTINATION > */
        for (Hero myHero : myHeroes) {
            Cell myHeroCell = myHero.getCurrentCell();

            //skip movement in respawnZone - only dodge at first:
            if (myHeroCell.isInMyRespawnZone()) {
                continue;
            }

            //skip hero if dead:
            if (myHero.getRemRespawnTime() != 0) {
                isInFirstLayout.put(myHero.getId(), false);
                continue;
            }

            //turns to true when reaches:
            else if (hasReachedToFirstLayout(myHero)) {
                isInFirstLayout.put(myHero.getId(), true);
            }

            System.out.print("Selected Hero: " + myHero.getName() + " " + myHero.getId() + " ");
            Cell destCell = getDestCell(world, myHero, new HashSet<>());

            //if other friendly hero is staying in destCell and (not going to move?):
            HashSet<Cell> blockedCells = new HashSet<>();
            int tries = 3;
            while (tries >= 1 && isCellFilledWithFriend(world, destCell, myHero)) {
                blockedCells.add(destCell);
                destCell = getDestCell(world, myHero, blockedCells);
                if (destCell.equals(myHeroCell)) {
                    break;
                }
                tries--;
            }

            if (destCell.equals(myHeroCell)) {
                isStayingThisPhase.put(myHero.getId(), true);
                System.out.println("Staying");
                continue;
            }

            //announce f.Hero won't stay:
            isStayingThisPhase.put(myHero.getId(), false);
            /* < / 1. FIND DESTINATION > */

            /* < 2. GET DIRECTION TO DEST > */
            //create a blocked cells list for where other f.Heroes are staying:
            ArrayList<Cell> blockedCellsList = new ArrayList<>();
            for (Hero h : myHeroes) {
                if (h.equals(myHero)) {
                    continue;
                }
                //only if staying in this phase, add to blockedList:
                if (isStayingThisPhase.get(h.getId())) {
                    blockedCellsList.add(h.getCurrentCell());
                }
            }
            //add respawn cells to blocked list:
            blockedCellsList.addAll(Arrays.asList(map.getMyRespawnZone()));

            //get directions:
            Direction[] dirs = world.getPathMoveDirections(myHeroCell, destCell, blockedCellsList);
            /* < / 2. GET DIRECTION TO DEST > */

            /* < 3. MOVE > */
            if (dirs.length == 0) {
                System.out.println("No direction");
                isStayingThisPhase.put(myHero.getId(), true);
            } else {
                world.moveHero(myHero, dirs[0]);
                System.out.println("Moving");
            }
            /* < / 3. MOVE > */

        }
    }

    @SuppressWarnings("Duplicates")
    public void actionTurn(World world) {

        System.out.println("\n<-- action -->");
        Hero[] myHeroes = world.getMyHeroes();
        Hero[] enemies = world.getOppHeroes();

        for (Hero myHero : myHeroes) {

            //first turn:
            if (myHero.getCurrentCell().isInMyRespawnZone()) {
                Cell targetCell;
                if (myHero.getId() == healerID) {
                    targetCell = firstDodgeCells[0];
                    world.castAbility(myHero, myHero.getAbility(AbilityName.HEALER_DODGE), targetCell);
                }
                else if (myHero.getId() == blasterID0) {
                    targetCell = firstDodgeCells[1];
                    world.castAbility(myHero, myHero.getAbility(AbilityName.BLASTER_DODGE), targetCell);
                }
                else if (myHero.getId() == blasterID1) {
                    targetCell = firstDodgeCells[2];
                    world.castAbility(myHero, myHero.getAbility(AbilityName.BLASTER_DODGE), targetCell);
                }
                else if (myHero.getId() == blasterID2) {
                    targetCell = firstDodgeCells[3];
                    world.castAbility(myHero, myHero.getAbility(AbilityName.BLASTER_DODGE), targetCell);
                }
            }

            //Do nothing when enemy not visible:
            if (enemies.length == 0) {
                return;
            }

            //skip hero if dead:
            if (myHero.getRemRespawnTime() != 0) {
                continue;
            }

            System.out.print("Selected Hero: " + myHero.getName() + " ");

            /* < 1.INIT > */
            HashMap<Hero, Cell> enemiesInSAttackRange = getEnemiesInSAttackRange(world, myHero, enemies);
            ArrayList<Cell> dodgeableCells = null;

            if (myHero.getDodgeAbilities()[0].isReady()) {
                int dodgeRange;
                if (myHero.getName().equals(HeroName.HEALER)) {
                    dodgeRange = healerDodgeRange;
                }
                else {
                    dodgeRange = blasterDodgeRange;
                }
                dodgeableCells = getDodgeableCells(world, myHero, dodgeRange, enemies);
            }

            //Healer zone: (if hero is healer, ability is ready, and others need healing)
            HashMap<Integer, Hero> friendsInHealerZone = null;
            if (myHero.getName().equals(HeroName.HEALER) &&
                    myHero.getAbility(AbilityName.HEALER_HEAL).isReady() &&
                    needsHealing.containsValue(Boolean.TRUE)) {
                friendsInHealerZone = getFriendsInHealerZone(world, myHero);
            }

            Hero targetHero = null;
            Cell targetCell = null;
            /* < / 1.INIT > */

            /* BLASTER */
            if (myHero.getName().equals(HeroName.BLASTER)) {
                Ability bombAttack = myHero.getAbility(AbilityName.BLASTER_BOMB);
                Ability simpleAttack = myHero.getAbility(AbilityName.BLASTER_ATTACK);
                Ability dodgeAbility = myHero.getAbility(AbilityName.BLASTER_DODGE);

                //Dodge to destCell:
                if (dodgeAbility.isReady() && !isStayingThisPhase.get(myHero.getId())) {
                    Cell destCell = getDestCell(world, myHero, new HashSet<>());
                    if (!destCell.equals(myHero.getCurrentCell())) {
                        targetCell = destCell;
                    }
                    if (targetCell != null) {
                        world.castAbility(myHero, dodgeAbility, targetCell);
                        System.out.println("Dodged to destCell");
                    }
                }

                //Dodge if dying and enemy close :
//                else if ((getHPPercentage(myHero) < blasterDodgeAlarm) && dodgeAbility.isReady() &&
//                        (dodgeableCells != null && !dodgeableCells.isEmpty())) {
//                    Cell bestDodgeCell = null;
//                    for (Cell dodgeCell : dodgeableCells) {
//                        if (bestDodgeCell == null) {
//                            bestDodgeCell = dodgeCell;
//                        } else {
//                            if (world.manhattanDistance(dodgeCell, myHero.getCurrentCell()) <
//                                    world.manhattanDistance(bestDodgeCell, myHero.getCurrentCell())) {
//                                bestDodgeCell = dodgeCell;
//                            }
//                        }
//                    }
//                    targetCell = bestDodgeCell;
//                    if (targetCell != null) {
//                        world.castAbility(myHero, dodgeAbility, targetCell);
//                        System.out.println("Dodged away");
//                    }
//                }

                else if (bombAttack.isReady()) {
                    //Find the best cell to throw the bomb:
                    targetCell = getBestCellToBomb(world, myHero, new HashSet<>());

                    if (targetCell != null) {
                        world.castAbility(myHero, bombAttack, targetCell);
                        System.out.println("Bomb-Attacked");
                    }
                }

                //Simple-Attack neighbors:
                else if (!enemiesInSAttackRange.isEmpty()) {
                    Hero weakestEnemy = null;
                    Cell hitCell = null;
                    for (Hero enemy : enemiesInSAttackRange.keySet()) {
                        if (!enemyDying.get(enemy.getId())) {
                            Cell tmpHitCell = enemiesInSAttackRange.get(enemy);
                            if (weakestEnemy == null) {
                                weakestEnemy = enemy;
                                hitCell = tmpHitCell;
                            } else {
                                if (enemy.getCurrentHP() < weakestEnemy.getCurrentHP()) {
                                    weakestEnemy = enemy;
                                    hitCell = tmpHitCell;
                                }
                            }
                        }
                    }
                    targetCell = hitCell;

                    if (targetCell != null) {
                        world.castAbility(myHero, simpleAttack, targetCell);
                        System.out.println("Simple-Attacked");
                        enemyDying.put(weakestEnemy.getId(), ((weakestEnemy.getCurrentHP() - damageBlasterAttack) <= 0));
                    }
                }

            }
            /* / BLASTER */


            /* HEALER */
            else if (myHero.getName().equals(HeroName.HEALER)) {
                Ability healingAbility = myHero.getAbility(AbilityName.HEALER_HEAL);
                Ability simpleAttack = myHero.getAbility(AbilityName.HEALER_ATTACK);
                Ability dodgeAbility = myHero.getAbility(AbilityName.HEALER_DODGE);

                //Dodge if HP is low:
                if ((getHPPercentage(myHero) < healerDodgeAlarm) && dodgeAbility.isReady() &&
                        (dodgeableCells != null && !dodgeableCells.isEmpty())) {
                    Cell bestDodgeCell = null;
                    for (Cell dodgeCell : dodgeableCells) {
                        if (bestDodgeCell == null) {
                            bestDodgeCell = dodgeCell;
                        }
                        else {
                            if (world.manhattanDistance(dodgeCell, myHero.getCurrentCell()) >
                            world.manhattanDistance(bestDodgeCell, myHero.getCurrentCell())) {
                                bestDodgeCell = dodgeCell;
                            }
                        }
                    }
                    targetCell = bestDodgeCell;
                    if (targetCell != null) {
                        world.castAbility(myHero, dodgeAbility, targetCell);
                        System.out.println("Dodged away");
                    }
                }


                //Simple-Attack neighbors:
                else if (!enemiesInSAttackRange.isEmpty()) {
                    Hero weakestEnemy = null;
                    Cell hitCell = null;
                    for (Hero enemy : enemiesInSAttackRange.keySet()) {
                        if (!enemyDying.get(enemy.getId())) {
                            Cell tmpHitCell = enemiesInSAttackRange.get(enemy);
                            if (weakestEnemy == null) {
                                weakestEnemy = enemy;
                                hitCell = tmpHitCell;
                            } else {
                                if (enemy.getCurrentHP() < weakestEnemy.getCurrentHP()) {
                                    weakestEnemy = enemy;
                                    hitCell = tmpHitCell;
                                }
                            }
                        }
                    }
                    targetCell = hitCell;

                    if (targetCell != null) {
                        world.castAbility(myHero, simpleAttack, targetCell);
                        System.out.println("Simple-Attacked");
                        enemyDying.put(weakestEnemy.getId(), ((weakestEnemy.getCurrentHP() - damageHealerAttack) <= 0));
                    }
                }

                //Heals the one who needs the most:
                else if (friendsInHealerZone != null) {


                    Hero needsHealTheMost = null;
                    for (Hero friend : world.getMyHeroes()) {
                        if (friend.getRemRespawnTime() != 0) {
                            continue;
                        }
                        if (friendsInHealerZone.containsKey(friend.getId())) {
                            if (needsHealing.get(friend.getId())) {
                                if (needsHealTheMost == null) {
                                    needsHealTheMost = friend;
                                } else if (getHPPercentage(friend) < getHPPercentage(needsHealTheMost)) {
                                    needsHealTheMost = friend;
                                }
                            }
                        }
                    }
                    targetHero = needsHealTheMost;
//
//                    if (friendsInHealerZone.containsKey(blasterID0) &&
//                            needsHealing.get(blasterID0)) {
//                        targetHero = friendsInHealerZone.get(blasterID0);
//                    }
//                    else if (friendsInHealerZone.containsKey(blasterID1) &&
//                            needsHealing.get(blasterID1)) {
//                        targetHero = friendsInHealerZone.get(blasterID1);
//                    }
//                    else if (friendsInHealerZone.containsKey(blasterID2) &&
//                            needsHealing.get(blasterID2)) {
//                        targetHero = friendsInHealerZone.get(blasterID2);
//                    }
//                    //A good doctor cures himself last!
//                    else if (friendsInHealerZone.containsKey(healerID) &&
//                            needsHealing.get(healerID)) {
//                        targetHero = myHero;
//                    }

                    if (targetHero != null) {
                        world.castAbility(myHero, healingAbility, targetHero.getCurrentCell());
                        System.out.println("Healed");
                    }
                }

            }
        }
        /* / HEALER */
    }




    @SuppressWarnings("Duplicates")
    private Cell getDestCell(World world, Hero myHero, HashSet<Cell> blockedCells) {
        Cell destCell = null;

        /* HEALER */
        if (myHero.getName().equals(HeroName.HEALER)) {
            //find cell of friend who needs healing the most:
            Hero needsHealTheMost = null;
            for (Hero friend : world.getMyHeroes()) {
                if (friend.getRemRespawnTime() != 0) {
                    continue;
                }
                if (needsHealing.get(friend.getId())) {
                    if (needsHealTheMost == null) {
                        needsHealTheMost = friend;
                    } else if (getHPPercentage(friend) < getHPPercentage(needsHealTheMost)) {
                        needsHealTheMost = friend;
                    }
                }
            }
            if (needsHealTheMost != null) {
                destCell = getClosestCellForHealer(world, myHero, needsHealTheMost, blockedCells);
            }
            else {
                destCell = healerPlace;
            }
        }

        /* BLASTER */
        else if (myHero.getName().equals(HeroName.BLASTER)) {
            for (Hero enemy : world.getOppHeroes()) {
                
                if (shouldBlastersMove) {
                    if (isInFirstLayout.get(myHero.getId())) {
                        //blaster1:
                        if (myHero.getId() == blasterID0) {
                            //if no enemy in attack range from blasterPlace:
                            if (!isEnemyInRangeFromCell(world, enemy, blasterPlaces[0], blasterAttackRange, blasterAttackEffect)) {
                                Hero closestEnemy = findClosestEnemyInObjZone(world, myHero, world.getOppHeroes());
                                if (closestEnemy == null) {
                                    System.out.println("closestEnemy  ------------  NULL");
                                }
                                destCell = findClosestCellToSAttack(world, myHero, blockedCells, closestEnemy, blasterAttackRange, blasterAttackEffect);
                                if (destCell == null) {
                                    System.out.println("closestCell  ------------  NULL");
                                    destCell = myHero.getCurrentCell();
                                }

                            } else {
                                destCell = blasterPlaces[0];
                            }
                        }

                        //blaster2:
                        else if (myHero.getId() == blasterID1) {
                            //if no enemy in attack range from blasterPlace:
                            if (myHero.getCurrentCell().isInObjectiveZone()) {
                                if (!isEnemyInRangeFromCell(world, enemy, blasterPlaces[1], blasterAttackRange, blasterAttackEffect)) {
                                    Hero closestEnemy = findClosestEnemyInObjZone(world, myHero, world.getOppHeroes());
                                    if (closestEnemy == null) {
                                        System.out.println("closestEnemy  ------------  NULL");
                                    }
                                    destCell = findClosestCellToSAttack(world, myHero, blockedCells, closestEnemy, blasterAttackRange, blasterAttackEffect);
                                    if (destCell == null) {
                                        System.out.println("closestCell  ------------  NULL");
                                        destCell = myHero.getCurrentCell();
                                    }
                                }
                            } else {
                                destCell = blasterPlaces[1];
                            }
                        }

                        //blaster3:
                        else if (myHero.getId() == blasterID2) {
                            //if no enemy in attack range from blasterPlace:
                            if (myHero.getCurrentCell().isInObjectiveZone()) {
                                if (!isEnemyInRangeFromCell(world, enemy, blasterPlaces[2], blasterAttackRange, blasterAttackEffect)) {
                                    Hero closestEnemy = findClosestEnemyInObjZone(world, myHero, world.getOppHeroes());
                                    if (closestEnemy == null) {
                                        System.out.println("closestEnemy  ------------  NULL");
                                    }
                                    destCell = findClosestCellToSAttack(world, myHero, blockedCells, closestEnemy, blasterAttackRange, blasterAttackEffect);
                                    if (destCell == null) {
                                        System.out.println("closestCell  ------------  NULL");
                                        destCell = myHero.getCurrentCell();
                                    }
                                }
                            } else {
                                destCell = blasterPlaces[2];
                            }
                        }
                    }
                    //reach layout first:
                    else {
                        if (myHero.getId() == blasterID0) {
                            destCell = blasterPlaces[0];
                        } else if (myHero.getId() == blasterID1) {
                            destCell = blasterPlaces[1];
                        } else if (myHero.getId() == blasterID2) {
                            destCell = blasterPlaces[2];
                        }
                    }
                }
                else {
                    if (myHero.getId() == blasterID0) {
                        destCell = blasterPlaces[0];
                    } else if (myHero.getId() == blasterID1) {
                        destCell = blasterPlaces[1];
                    } else if (myHero.getId() == blasterID2) {
                        destCell = blasterPlaces[2];
                    }
                }

            }
        }

        return destCell;
    }

    private boolean canHeroMoveToCell(Cell cell, ArrayList<Cell> myOtherHeroesCells) {
        return !cell.isWall() && !cell.isInOppRespawnZone() && !myOtherHeroesCells.contains(cell);
    }

    private Cell getClosestObjCell(World world, Cell heroCell, HashSet<Cell> blockedCells) {
        if (heroCell.isInObjectiveZone() && !blockedCells.contains(heroCell)) {
            return heroCell;
        }
        Cell closestObjCell = null;
        for (Cell objCell : objCells) {
            if (closestObjCell == null && (blockedCells.isEmpty() || !blockedCells.contains(objCell))) {
                closestObjCell = objCell;
            }
            else if (world.manhattanDistance(heroCell, objCell) < world.manhattanDistance(heroCell, closestObjCell)) {
                if (blockedCells.isEmpty() || !blockedCells.contains(objCell)) {
                    closestObjCell = objCell;
                }
            }
        }
        return closestObjCell;
    }

    private boolean isCellFilledWithFriend(World world, Cell destCell, Hero myHero) {
        for (Hero hero : world.getMyHeroes()) {
            if (hero.equals(myHero)) {
                continue;
            }
            if (hero.getCurrentCell().equals(destCell)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("Duplicates")
    private int countEnemiesAffectedByBomb(World world, Cell bombedCell) {
        int affectedEnemies = 0;
        HashSet<Cell> affectedCells = new HashSet<>();
        int range = bombEffectRange;
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                if (map.isInMap(bombedCell.getRow() + i, bombedCell.getColumn() + j)) {
                    Cell tmpCell = map.getCell(bombedCell.getRow() + i, bombedCell.getColumn() + j);
                    if (world.manhattanDistance(bombedCell, tmpCell) <= range) {
                        affectedCells.add(tmpCell);
                    }
                }
            }
        }
        for (Hero enemy : world.getOppHeroes()) {
            if (affectedCells.contains(enemy.getCurrentCell())) {
                affectedEnemies++;
            }
        }
        return affectedEnemies;
    }

    private Cell getBestCellToBomb(World world, Hero myHero, HashSet<Cell> blockedCells) {
        Cell targetCell = null;
        int maxAffected = 0;
        Cell blasterCell = myHero.getCurrentCell();
        int range = blasterBombAttackRange;
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                //is the cell even in map??
                if (map.isInMap(blasterCell.getRow() + i, blasterCell.getColumn() + j)) {
                    Cell aroundCell = map.getCell(blasterCell.getRow() + i, blasterCell.getColumn() + j);
                    if (!blockedCells.contains(aroundCell)) {
                        if (world.manhattanDistance(aroundCell, myHero.getCurrentCell()) <= range) {
                            //most effecting target:
                            int tmp = countEnemiesAffectedByBomb(world, aroundCell);
                            if (tmp > maxAffected) {
                                maxAffected = tmp;
                                targetCell = aroundCell;
                            }
                        }
                    }
                }
            }
        }
        if ((targetCell != null) && (countEnemiesAffectedByBomb(world, targetCell) == 0)) {
            return null;
        }
        return targetCell;
    }

    private Cell getClosestCellForHealer(World world, Hero healer, Hero destHero, HashSet<Cell> blockedCells) {
        if (healer.equals(destHero)) {
            return healer.getCurrentCell();
        }
        Cell closestHealCell = null;
        Cell destHeroCell = destHero.getCurrentCell();
        int range = healingRange;
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                //if the cell exists in map:
                if (map.isInMap(destHeroCell.getRow() + i, destHeroCell.getColumn() + j)) {
                    Cell tmpCell = map.getCell(destHeroCell.getRow() + i, destHeroCell.getColumn() + j);
                    if (world.manhattanDistance(tmpCell, destHeroCell) <= range) {
                        if (tmpCell.isInObjectiveZone()) {
                            //if it is the first cell to check and is not from blockedCells:
                            if (blockedCells.isEmpty() || !blockedCells.contains(tmpCell)) {
                                if (closestHealCell == null) {
                                    closestHealCell = tmpCell;
                                } else if (world.manhattanDistance(healer.getCurrentCell(), tmpCell) <
                                        world.manhattanDistance(healer.getCurrentCell(), closestHealCell)) {
                                    closestHealCell = tmpCell;
                                }
                            }
                        }
                    }
                }
            }
        }
        return closestHealCell;
    }

    private HashMap<Integer, Hero> getFriendsInHealerZone(World world, Hero healer) {
        HashMap<Integer, Hero> friendsInHealerZone = new HashMap<>();
        Cell healerCell = healer.getCurrentCell();
        for (int i = -healingRange; i <= healingRange; ++i) {
            for (int j = -healingRange; j <= healingRange; ++j) {
                if (map.isInMap(healerCell.getRow() + i, healerCell.getColumn() + j)) {
                    Cell aroundCell = map.getCell(healerCell.getRow() + i, healerCell.getColumn() + j);
                    if (world.manhattanDistance(healerCell, aroundCell) <= healingRange) {
                        for (Hero hero : world.getMyHeroes()) {
                            if (hero.getCurrentCell().equals(aroundCell)) {
                                friendsInHealerZone.put(hero.getId(), hero);
                            }
                        }
                    }
                }
            }
        }
        return friendsInHealerZone;
    }

    private ArrayList<Cell> getDodgeableCells(World world, Hero myHero, int range, Hero[] enemies) {
        ArrayList<Cell> myOtherHeroesCells = getMyOtherHeroesCells(world, myHero);
        ArrayList<Cell> dodgeableCells = new ArrayList<>();
        Cell myHeroCell = myHero.getCurrentCell();
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                if (map.isInMap(myHeroCell.getRow() + i, myHeroCell.getColumn() + j)) {
                    Cell aroundCell = map.getCell(myHeroCell.getRow() + i, myHeroCell.getColumn() + j);
                    if (world.manhattanDistance(myHeroCell, aroundCell) <= range) {
                        if (aroundCell.isInObjectiveZone() && canHeroMoveToCell(aroundCell, myOtherHeroesCells)) {
                            for (Hero enemy : enemies) {
                                if (!enemy.getCurrentCell().equals(aroundCell)) {
                                    dodgeableCells.add(aroundCell);
                                }
                            }
                        }
                    }
                }
            }
        }
        return dodgeableCells;
    }

    private ArrayList<Cell> getMyOtherHeroesCells(World world, Hero myHero) {
        ArrayList<Cell> myHeroesCells = new ArrayList<>();
        for (Hero hero : world.getMyHeroes()) {
            if (!myHero.equals(hero)) {
                myHeroesCells.add(hero.getCurrentCell());
            }
        }
        return myHeroesCells;
    }

    private HashMap<Hero, Cell> getEnemiesInSAttackRange(World world, Hero myHero, Hero[] enemies) {
        HashMap<Hero, Cell> enemiesInRange = new HashMap<>();
        int range = 0;
        int effect = 0;
        if (myHero.getName().equals(HeroName.HEALER)) {
            range = healerAttackRange;
        } else if (myHero.getName().equals(HeroName.BLASTER)) {
            range = blasterAttackRange;
            effect = blasterAttackEffect;
        }

        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                if (map.isInMap(myHero.getCurrentCell().getRow() + i, myHero.getCurrentCell().getColumn() + j)) {
                    Cell tmpCell = map.getCell(myHero.getCurrentCell().getRow() + i, myHero.getCurrentCell().getColumn() + j);
                    if (world.manhattanDistance(myHero.getCurrentCell(), tmpCell) <= range) {
                        if (world.isInVision(myHero.getCurrentCell(), tmpCell)) {
                            //for each cell around myHero, check the attack effect around it!
                            for (int k = -effect; k <= effect; ++k) {
                                for (int h = -effect; h <= effect; ++h) {
                                    Cell tmpCell2 = map.getCell(tmpCell.getRow() + k, tmpCell.getColumn() + h);
                                    //selected cell and its neighbor:
                                    if (world.manhattanDistance(tmpCell, tmpCell2) <= effect) {
                                        for (Hero enemy : enemies) {
                                            if (enemy.getCurrentCell().equals(tmpCell2)) {
                                                //if enemy in effect cell, shoot the cell in range of myHero!
                                                enemiesInRange.put(enemy, tmpCell);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return enemiesInRange;
    }

    @SuppressWarnings("Duplicates")
    private boolean isEnemyVisibleToHero(World world, Hero enemy, Hero myHero) {
        return world.isInVision(myHero.getCurrentCell(), enemy.getCurrentCell());
    }

    private int getHPPercentage(Hero hero) {
        return (hero.getCurrentHP() * 100) / hero.getMaxHP();
    }

    private Cell[] findFirstDodgeCells(World world, Hero[] myHeroes) {
        Cell[] firstCells = new Cell[4];
        Hero hero0, hero1, hero2, hero3;
        hero0 = myHeroes[0];
        hero1 = myHeroes[1];
        hero2 = myHeroes[2];
        hero3 = myHeroes[3];
        HashSet<Cell> blockedList = new HashSet<>();

        firstCells[0] = getClosestObjCell(world, hero0.getCurrentCell(), blockedList);
        blockedList.add(firstCells[0]);

        firstCells[1] = getClosestObjCell(world, hero1.getCurrentCell(), blockedList);
        blockedList.add(firstCells[1]);

        firstCells[2] = getClosestObjCell(world, hero2.getCurrentCell(), blockedList);
        blockedList.add(firstCells[2]);

        firstCells[3] = getClosestObjCell(world, hero3.getCurrentCell(), blockedList);

        return firstCells;
    }



    private Cell[] findBlasterCells(World world) {
        Cell[] blasterCells = {null, null, null};

        //blaster 1:
        int distance = 1;
        boolean found = false;
        while (!found) {
            Cell closest = null;
            for (int i = -distance; i <= distance; ++i) {
                for (int j = -distance; j <= distance; ++j) {
                    if (map.isInMap(healerPlace.getRow() + i, healerPlace.getColumn() + j)) {
                        Cell tmpCell = map.getCell(healerPlace.getRow() + i, healerPlace.getColumn() + j);
                        if (world.manhattanDistance(tmpCell, healerPlace) <= distance) {
                            if (tmpCell.isInObjectiveZone() && !tmpCell.isWall()) {
                                if ((closest == null) &&
                                        (world.manhattanDistance(healerPlace, tmpCell) > minHeroDist)) {
                                    closest = tmpCell;
                                }
                                else {
                                    //closest to my respawn zone:
                                    if ((world.manhattanDistance(healerPlace, tmpCell) > minHeroDist) &&
                                            (world.manhattanDistance(map.getMyRespawnZone()[0], tmpCell) <
                                                    (world.manhattanDistance(map.getMyRespawnZone()[0], closest)))) {
                                        closest = tmpCell;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (closest != null) {
                blasterCells[0] = closest;
                found = true;
            }
            else {
                ++distance;
            }
        }

        //blaster 2:
        distance = 1;
        found = false;
        while (!found) {
            Cell closest = null;
            for (int i = -distance; i <= distance; ++i) {
                for (int j = -distance; j <= distance; ++j) {
                    if (map.isInMap(healerPlace.getRow() + i, healerPlace.getColumn() + j)) {
                        Cell tmpCell = map.getCell(healerPlace.getRow() + i, healerPlace.getColumn() + j);
                        if (world.manhattanDistance(tmpCell, healerPlace) <= distance) {
                            if (tmpCell.isInObjectiveZone() && !tmpCell.isWall()) {
                                if (!tmpCell.equals(blasterCells[0]) &&
                                        (closest == null) &&
                                        (world.manhattanDistance(healerPlace, tmpCell) > minHeroDist - 1) &&
                                        (world.manhattanDistance(blasterCells[0], tmpCell) > minHeroDist)) {
                                    closest = tmpCell;
                                }
                                else {
                                    if (!tmpCell.equals(blasterCells[0]) &&
                                            (world.manhattanDistance(healerPlace, tmpCell) > minHeroDist - 1) &&
                                            (world.manhattanDistance(blasterCells[0], tmpCell) > minHeroDist) &&
                                            (world.manhattanDistance(map.getMyRespawnZone()[0], tmpCell) <
                                                    world.manhattanDistance(map.getMyRespawnZone()[0], closest))) {
                                        closest = tmpCell;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (closest != null) {
                blasterCells[1] = closest;
                found = true;
            }
            else {
                ++distance;
            }
        }

        //blaster 3:
        distance = 1;
        found = false;
        while (!found) {
            Cell closest = null;
            for (int i = -distance; i <= distance; ++i) {
                for (int j = -distance; j <= distance; ++j) {
                    if (map.isInMap(healerPlace.getRow() + i, healerPlace.getColumn() + j)) {
                        Cell tmpCell = map.getCell(healerPlace.getRow() + i, healerPlace.getColumn() + j);
                        if (world.manhattanDistance(tmpCell, healerPlace) <= distance) {
                            if (tmpCell.isInObjectiveZone() && !tmpCell.isWall()) {
                                if ((!tmpCell.equals(blasterCells[0])) &&
                                        (!tmpCell.equals(blasterCells[1])) &&
                                        (closest == null) &&
                                        (world.manhattanDistance(healerPlace, tmpCell) > minHeroDist) &&
                                        (world.manhattanDistance(blasterCells[0], tmpCell) > minHeroDist) &&
                                        (world.manhattanDistance(blasterCells[1], tmpCell) > minHeroDist)) {
                                    closest = tmpCell;
                                }
                                else {
                                    if ((!tmpCell.equals(blasterCells[0])) &&
                                            (!tmpCell.equals(blasterCells[1])) &&
                                            (world.manhattanDistance(healerPlace, tmpCell) > minHeroDist) &&
                                            (world.manhattanDistance(blasterCells[0], tmpCell) > minHeroDist) &&
                                            (world.manhattanDistance(blasterCells[1], tmpCell) > minHeroDist) &&
                                            (world.manhattanDistance(map.getMyRespawnZone()[0], tmpCell) <
                                                    world.manhattanDistance(map.getMyRespawnZone()[0], closest))) {
                                        closest = tmpCell;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (closest != null) {
                blasterCells[2] = closest;
                found = true;
            }
            else {
                ++distance;
            }
        }
        return blasterCells;
    }

    private Cell findClosestCellToSAttack(World world, Hero myHero, HashSet<Cell> blockedList, Hero closestEnemy, int attackRange, int effectRange) {
        if (closestEnemy == null) {
            return null;
        }
        Cell myHeroCell = myHero.getCurrentCell();
        Cell closestCell = null;
        int range = movePhases * 2;
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                Cell tmpCell = map.getCell(myHeroCell.getRow() + i, myHeroCell.getColumn() + j);
                if ((tmpCell.isInObjectiveZone()) && (!tmpCell.isWall()) && (!blockedList.contains(tmpCell))) {
                    //if we can hit enemy from that cell:
                    if (isEnemyInRangeFromCell(world, closestEnemy, tmpCell, attackRange, effectRange)) {
                        //distance from layout places:
                        if (isDistanceCellFurtherThanOtherPlaces(world, myHero, tmpCell, minHeroDist - 2)) { //TODO: ?
                            //distance from heroes current cells:
                            for (Hero otherHero : world.getMyHeroes()) {
                                if (otherHero.equals(myHero) || (otherHero.getId() == healerID)) {
                                    continue;
                                }
                                else if (world.manhattanDistance(tmpCell, otherHero.getCurrentCell()) < minHeroDist - 2) { //TODO: ?
                                    break;
                                }
                                else {
                                    if (closestCell == null) {
                                        closestCell = tmpCell;
                                    } else {
                                        if (world.manhattanDistance(myHeroCell, tmpCell) <
                                                world.manhattanDistance(myHeroCell, closestCell)) {
                                            closestCell = tmpCell;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return closestCell;
    }

    @SuppressWarnings("Duplicates")
    private Hero findClosestEnemyInObjZone(World world, Hero myHero, Hero[] enemies) {
        Hero closestEnemy = null;
        for (Hero enemy : enemies) {
            if (enemy.getCurrentCell().isInObjectiveZone()) {
                if (closestEnemy == null) {
                    closestEnemy = enemy;
                }
                else {
                    if (world.manhattanDistance(myHero.getCurrentCell(), enemy.getCurrentCell()) <
                            world.manhattanDistance(myHero.getCurrentCell(), closestEnemy.getCurrentCell())) {
                        closestEnemy = enemy;
                    }
                }
            }
        }
        return closestEnemy;
    }

    private boolean isEnemyInRangeFromCell(World world, Hero enemy, Cell cell, int attackRange, int effectRange) {
        if (world.manhattanDistance(cell, enemy.getCurrentCell()) <= attackRange + effectRange) {
            return world.isInVision(cell, enemy.getCurrentCell());
        }
        return false;
    }

    private boolean hasReachedToFirstLayout(Hero myHero) {
        if (myHero.getId() == blasterID0) {
            return myHero.getCurrentCell().equals(blasterPlaces[0]);
        }
        else if (myHero.getId() == blasterID1) {
            return myHero.getCurrentCell().equals(blasterPlaces[1]);
        }
        else if (myHero.getId() == blasterID2) {
            return myHero.getCurrentCell().equals(blasterPlaces[2]);
        }
        else {
            return myHero.getCurrentCell().equals(healerPlace);
        }
    }

    private boolean isDistanceCellFurtherThanOtherPlaces(World world, Hero myHero, Cell tmpCell, int range) {
        if (myHero.getId() == healerID) {
            if ((world.manhattanDistance(tmpCell, blasterPlaces[0]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[1]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[2]) > range)) {
                return true;
            }
        }
        else if (myHero.getId() == blasterID0) {
            if ((world.manhattanDistance(tmpCell, healerPlace) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[1]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[2]) > range)) {
                return true;
            }
        }
        else if (myHero.getId() == blasterID1) {
            if ((world.manhattanDistance(tmpCell, healerPlace) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[0]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[2]) > range)) {
                return true;
            }
        }
        else if (myHero.getId() == blasterID2) {
            if ((world.manhattanDistance(tmpCell, healerPlace) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[0]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[1]) > range)) {
                return true;
            }
        }
        return false;
    }

}