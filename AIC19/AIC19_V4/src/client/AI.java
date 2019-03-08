package client;

import client.Salek.CellAndNumAndEnemyList;
import client.model.*;
import client.model.Map;

import java.util.*;

public class AI {

    private Map map;
    private Cell[] objCells;
    private int movePhases = 6;
    private HashMap<Integer, Boolean> isStayingThisPhase = new HashMap<>();
    private HashMap<Integer, Boolean> isInFirstLayout = new HashMap<>();
    private HashMap<Integer, Boolean> enemyDying = new HashMap<>();

    private boolean moveInit = true;
    private Cell[] blasterPlaces = new Cell[4];
    private Cell[] firstDodgeCells;
    private Cell[] mapSideCells = new Cell[4];

    private int blasterID0, blasterID1, blasterID2, blasterID3;
    private int minHeroDist = 5;
    private boolean shouldBlastersMove = false;

    //damages:
    private int damageBlasterAttack = 20;
    private int damageBlasterBomb = 40;

    //blaster:
    private int blasterAttackRange = 4;
    private int blasterAttackEffect = 1;
    private int blasterBombAttackRange = 5;
    private int bombEffectRange = 2;
    private int blasterDodgeRange = 4;



    public void preProcess(World world) {
        System.out.println("<-- pre process -->");
        blasterID0 = blasterID1 = blasterID2 = blasterID3 = -1;
        map = world.getMap();
        objCells = map.getObjectiveZone();


        mapSideCells[0] = map.getCell(15, 0);
        mapSideCells[1] = map.getCell(15, 30);
        mapSideCells[2] = map.getCell(0, 15);
        mapSideCells[3] = map.getCell(30, 15);


        blasterPlaces[3] = getClosestObjCell(world, mapSideCells[0], new HashSet<>());
        blasterPlaces[0] = getClosestObjCell(world, mapSideCells[1], new HashSet<>());
        blasterPlaces[1] = getClosestObjCell(world, mapSideCells[2], new HashSet<>());
        blasterPlaces[2] = getClosestObjCell(world, mapSideCells[3], new HashSet<>());

        boolean closeHeroesAlarm = false;
        closeAlarm:
        {
            for (Cell bCell1 : blasterPlaces) {
                for (Cell bCell2 : blasterPlaces) {
                    if (!bCell1.equals(bCell2)) {
                        if (world.manhattanDistance(bCell1, bCell2) < minHeroDist) {
                            closeHeroesAlarm = true;
                            break closeAlarm;
                        }
                    }
                }
            }
        }

        if (closeHeroesAlarm) {
            System.out.println("Changed default heroes layout.");
            blasterPlaces[0] = blasterPlaces[1] = blasterPlaces[2] = blasterPlaces[3] = null;
            blasterPlaces[3] = getClosestObjCell(world, map.getMyRespawnZone()[0], new HashSet<>());
            boolean findPlaces = true;
            while (findPlaces) {
                Cell[] otherPlaces = findBlasterCells(world);
                blasterPlaces[0] = otherPlaces[0];
                blasterPlaces[1] = otherPlaces[1];
                blasterPlaces[2] = otherPlaces[2];
                int num = 0;
                for (Cell cell : blasterPlaces) {
                    if (cell != null) {
                        num++;
                    }
                }
                if (num == 4) {
                    findPlaces = false;
                }
                else {
                    minHeroDist--;
                }
            }
            System.out.println("Changed heroes distances to: " + minHeroDist);
        }

    }

    public void pickTurn(World world) {
        System.out.println("<-- pick -->");
        if (world.getCurrentTurn() == 0) {
            world.pickHero(HeroName.BLASTER);
        } else if (world.getCurrentTurn() == 1) {
            world.pickHero(HeroName.BLASTER);
        } else if (world.getCurrentTurn() == 2) {
            world.pickHero(HeroName.BLASTER);
        } else if (world.getCurrentTurn() == 3) {
            world.pickHero(HeroName.BLASTER);
        }
    }

    public void moveTurn(World world) {

        System.out.println("\n<-- move phase - turn: " + world.getCurrentTurn() + " -->");
        Hero[] myHeroes = world.getMyHeroes();

        /* < INIT > */
        if (moveInit) {
            //isStaying and heroesID:
            for (Hero myHero : world.getMyHeroes()) {
                isStayingThisPhase.put(myHero.getId(), true);
                isInFirstLayout.put(myHero.getId(), false);

                if (myHero.getName().equals(HeroName.BLASTER)) {
                    if (blasterID3 == -1) {
                        blasterID3 = myHero.getId();
                    }
                    else if (blasterID0 == -1) {
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
                if (myHero.getId() == blasterID3) {
                    targetCell = firstDodgeCells[0];
                    world.castAbility(myHero, myHero.getAbility(AbilityName.BLASTER_DODGE), targetCell);
                } else if (myHero.getId() == blasterID0) {
                    targetCell = firstDodgeCells[1];
                    world.castAbility(myHero, myHero.getAbility(AbilityName.BLASTER_DODGE), targetCell);
                } else if (myHero.getId() == blasterID1) {
                    targetCell = firstDodgeCells[2];
                    world.castAbility(myHero, myHero.getAbility(AbilityName.BLASTER_DODGE), targetCell);
                } else if (myHero.getId() == blasterID2) {
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

            if (myHero.getAbility(AbilityName.BLASTER_DODGE).isReady()) {
                int dodgeRange;

                dodgeRange = blasterDodgeRange;

                dodgeableCells = getDodgeableCells(world, myHero, dodgeRange, enemies);
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

                //kills with SAttack (not bomb) if he can:
                if (!enemiesInSAttackRange.isEmpty()) {
                    for (Hero enemy : enemiesInSAttackRange.keySet()) {
                        if (enemy.getCurrentHP() <= damageBlasterAttack) {
                            targetCell = enemiesInSAttackRange.get(enemy);

                            if (targetCell != null) {
                                world.castAbility(myHero, simpleAttack, targetCell);
                                System.out.println("Simple-Attacked");
                                enemyDying.put(enemy.getId(), true);
                            }
                        }
                    }
                }

                //Bomb:
                if (bombAttack.isReady()) {
                    //Find the best cell to throw the bomb:
                    targetCell = getBestCellToBomb(world, myHero, new HashSet<>());

                    if (targetCell != null) {
                        world.castAbility(myHero, bombAttack, targetCell);
                        System.out.println("Bomb-Attacked");
                    }
                }

                //Simple-Attack neighbors:
                if (!enemiesInSAttackRange.isEmpty()) {
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


        }
    }




    @SuppressWarnings("Duplicates")
    private Cell getDestCell(World world, Hero myHero, HashSet<Cell> blockedCells) {
        Cell destCell = null;

        /* BLASTER */
        if (myHero.getName().equals(HeroName.BLASTER)) {
            for (Hero enemy : world.getOppHeroes()) {
                
                if (shouldBlastersMove) {
                    if (isInFirstLayout.get(myHero.getId())) {

                        //blaster3:
                        if (myHero.getId() == blasterID3) {
                            //if no enemy in attack range from blasterPlace:
                            if (!isEnemyInRangeFromCell(world, enemy, blasterPlaces[3], blasterAttackRange, blasterAttackEffect)) {
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
                                destCell = blasterPlaces[3];
                            }
                        }

                        //blaster1:
                        else if (myHero.getId() == blasterID0) {
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
                        if (myHero.getId() == blasterID3) {
                            destCell = blasterPlaces[3];
                        } else if (myHero.getId() == blasterID0) {
                            destCell = blasterPlaces[0];
                        } else if (myHero.getId() == blasterID1) {
                            destCell = blasterPlaces[1];
                        } else if (myHero.getId() == blasterID2) {
                            destCell = blasterPlaces[2];
                        }
                    }
                }
                else {
                    if (myHero.getId() == blasterID3) {
                        destCell = blasterPlaces[3];
                    } else if (myHero.getId() == blasterID0) {
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
    private CellAndNumAndEnemyList numAndEnemiesAffectedByBomb(World world, Cell bombedCell) {
        CellAndNumAndEnemyList numAndAffectedEnemies;
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
        ArrayList<Hero> affectedEnemies = new ArrayList<>();
        int numAffectedEnemies = 0;
        for (Hero enemy : world.getOppHeroes()) {
            //check if only his not going to die:
            if (!enemyDying.get(enemy.getId())) {
                if (affectedCells.contains(enemy.getCurrentCell())) {
                    affectedEnemies.add(enemy);
                    numAffectedEnemies++;
                }
            }
        }
        numAndAffectedEnemies = new CellAndNumAndEnemyList(bombedCell, numAffectedEnemies, affectedEnemies);
        return numAndAffectedEnemies;
    }

    @SuppressWarnings("Duplicates")
    private Cell getBestCellToBomb(World world, Hero myHero, HashSet<Cell> blockedCells) {
        Cell targetCell = null;
        int maxAffected = 0;
        int range = blasterBombAttackRange;
        ArrayList<CellAndNumAndEnemyList> oneAffected = new ArrayList<>();
        ArrayList<CellAndNumAndEnemyList> twoAffected = new ArrayList<>();
        ArrayList<CellAndNumAndEnemyList> threeAffected = new ArrayList<>();
        ArrayList<CellAndNumAndEnemyList> fourAffected = new ArrayList<>();
        Cell blasterCell = myHero.getCurrentCell();
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                //is the cell even in map??
                if (map.isInMap(blasterCell.getRow() + i, blasterCell.getColumn() + j)) {
                    Cell aroundCell = map.getCell(blasterCell.getRow() + i, blasterCell.getColumn() + j);
                    if (!blockedCells.contains(aroundCell)) {
                        if (world.manhattanDistance(aroundCell, myHero.getCurrentCell()) <= range) {
                            //most effecting target:
                            CellAndNumAndEnemyList bombHitCell = numAndEnemiesAffectedByBomb(world, aroundCell);
                            if (bombHitCell.getAffectedNum() > maxAffected) {
                                maxAffected = bombHitCell.getAffectedNum();
                            }

                            if ((maxAffected == 1) && (bombHitCell.getAffectedNum() == 1)) {
                                oneAffected.add(bombHitCell);
                            }
                            else if ((maxAffected == 2) && (bombHitCell.getAffectedNum() == 2)) {
                                twoAffected.add(bombHitCell);
                            }
                            else if ((maxAffected == 3) && (bombHitCell.getAffectedNum() == 3)) {
                                threeAffected.add(bombHitCell);
                            }
                            else if ((maxAffected == 4) && (bombHitCell.getAffectedNum() == 4)) {
                                fourAffected.add(bombHitCell);
                            }

                        }
                    }
                }
            }
        }

        int lowestHpSum = Integer.MAX_VALUE;
        CellAndNumAndEnemyList chosenOne = null;
        if (maxAffected == 0) {
            return null;
        }
        else if (maxAffected == 1) {
            //for each cell which affects enemies:
            for (CellAndNumAndEnemyList cne : oneAffected) {
                int hpSum = 0;
                for (Hero affectedEnemy : cne.getEnemyList()) {
                    hpSum = hpSum + affectedEnemy.getCurrentHP();
                }
                if (hpSum < lowestHpSum) {
                    lowestHpSum = hpSum;
                    chosenOne = cne;
                }
            }
        }
        else if (maxAffected == 2) {
            //for each cell which affects enemies:
            for (CellAndNumAndEnemyList cne : twoAffected) {
                int hpSum = 0;
                for (Hero affectedEnemy : cne.getEnemyList()) {
                    hpSum = hpSum + affectedEnemy.getCurrentHP();
                }
                if (hpSum < lowestHpSum) {
                    lowestHpSum = hpSum;
                    chosenOne = cne;
                }
            }
        }
        else if (maxAffected == 3) {
            //for each cell which affects enemies:
            for (CellAndNumAndEnemyList cne : threeAffected) {
                int hpSum = 0;
                for (Hero affectedEnemy : cne.getEnemyList()) {
                    hpSum = hpSum + affectedEnemy.getCurrentHP();
                }
                if (hpSum < lowestHpSum) {
                    lowestHpSum = hpSum;
                    chosenOne = cne;
                }
            }
        }
        else if (maxAffected == 4) {
            //for each cell which affects enemies:
            for (CellAndNumAndEnemyList cne : fourAffected) {
                int hpSum = 0;
                for (Hero affectedEnemy : cne.getEnemyList()) {
                    hpSum = hpSum + affectedEnemy.getCurrentHP();
                }
                if (hpSum < lowestHpSum) {
                    lowestHpSum = hpSum;
                    chosenOne = cne;
                }
            }
        }

        if (chosenOne == null) {
            System.out.println("WHAT THE HELL -------------- NULL");
        }
        else {
            //this is the chosen one:
            targetCell = chosenOne.getCell();
            //announce if enemies die:
            for (Hero affectedEnemy : chosenOne.getEnemyList()) {
                if (affectedEnemy.getCurrentHP() <= damageBlasterBomb) {
                    enemyDying.put(affectedEnemy.getId(), true);
                }
            }
        }
        return targetCell;
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
        if (myHero.getName().equals(HeroName.BLASTER)) {
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
                        if (isDistanceCellFurtherThanOtherPlaces(world, myHero, tmpCell, minHeroDist)) { //TODO: ?
                            //distance from heroes current cells:
                            for (Hero otherHero : world.getMyHeroes()) {
                                if (otherHero.equals(myHero)) {
                                    continue;
                                }
                                else if (world.manhattanDistance(tmpCell, otherHero.getCurrentCell()) < minHeroDist) { //TODO: ?
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
            return myHero.getCurrentCell().equals(blasterPlaces[3]);
        }
    }

    private boolean isDistanceCellFurtherThanOtherPlaces(World world, Hero myHero, Cell tmpCell, int range) {
        if (myHero.getId() == blasterID3) {
            if ((world.manhattanDistance(tmpCell, blasterPlaces[0]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[1]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[2]) > range)) {
                return true;
            }
        }
        else if (myHero.getId() == blasterID0) {
            if ((world.manhattanDistance(tmpCell, blasterPlaces[3]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[1]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[2]) > range)) {
                return true;
            }
        }
        else if (myHero.getId() == blasterID1) {
            if ((world.manhattanDistance(tmpCell, blasterPlaces[3]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[0]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[2]) > range)) {
                return true;
            }
        }
        else if (myHero.getId() == blasterID2) {
            if ((world.manhattanDistance(tmpCell, blasterPlaces[3]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[0]) > range) &&
                    (world.manhattanDistance(tmpCell, blasterPlaces[1]) > range)) {
                return true;
            }
        }
        return false;
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
                    if (map.isInMap(blasterPlaces[3].getRow() + i, blasterPlaces[3].getColumn() + j)) {
                        Cell tmpCell = map.getCell(blasterPlaces[3].getRow() + i, blasterPlaces[3].getColumn() + j);
                        if (world.manhattanDistance(tmpCell, blasterPlaces[3]) <= distance) {
                            if (tmpCell.isInObjectiveZone() && !tmpCell.isWall()) {
                                if ((closest == null) &&
                                        (world.manhattanDistance(blasterPlaces[3], tmpCell) > minHeroDist)) {
                                    closest = tmpCell;
                                }
                                else {
                                    //closest to my respawn zone:
                                    if ((world.manhattanDistance(blasterPlaces[3], tmpCell) > minHeroDist) &&
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
                if (distance > movePhases * 2) {
                    blasterCells[0] = null;
                    found = true;
                }
                else {
                    ++distance;
                }
            }
        }

        //blaster 2:
        distance = 1;
        found = false;
        while (!found) {
            Cell closest = null;
            for (int i = -distance; i <= distance; ++i) {
                for (int j = -distance; j <= distance; ++j) {
                    if (map.isInMap(blasterPlaces[3].getRow() + i, blasterPlaces[3].getColumn() + j)) {
                        Cell tmpCell = map.getCell(blasterPlaces[3].getRow() + i, blasterPlaces[3].getColumn() + j);
                        if (world.manhattanDistance(tmpCell, blasterPlaces[3]) <= distance) {
                            if (tmpCell.isInObjectiveZone() && !tmpCell.isWall()) {
                                if (!tmpCell.equals(blasterCells[0]) &&
                                        (closest == null) &&
                                        (world.manhattanDistance(blasterPlaces[3], tmpCell) > minHeroDist) &&
                                        (world.manhattanDistance(blasterCells[0], tmpCell) > minHeroDist)) {
                                    closest = tmpCell;
                                }
                                else {
                                    if (!tmpCell.equals(blasterCells[0]) &&
                                            (world.manhattanDistance(blasterPlaces[3], tmpCell) > minHeroDist) &&
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
                if (distance > movePhases * 2) {
                    blasterCells[1] = null;
                    found = true;
                }
                else {
                    ++distance;
                }
            }
        }

        //blaster 3:
        distance = 1;
        found = false;
        while (!found) {
            Cell closest = null;
            for (int i = -distance; i <= distance; ++i) {
                for (int j = -distance; j <= distance; ++j) {
                    if (map.isInMap(blasterPlaces[3].getRow() + i, blasterPlaces[3].getColumn() + j)) {
                        Cell tmpCell = map.getCell(blasterPlaces[3].getRow() + i, blasterPlaces[3].getColumn() + j);
                        if (world.manhattanDistance(tmpCell, blasterPlaces[3]) <= distance) {
                            if (tmpCell.isInObjectiveZone() && !tmpCell.isWall()) {
                                if ((!tmpCell.equals(blasterCells[0])) &&
                                        (!tmpCell.equals(blasterCells[1])) &&
                                        (closest == null) &&
                                        (world.manhattanDistance(blasterPlaces[3], tmpCell) > minHeroDist) &&
                                        (world.manhattanDistance(blasterCells[0], tmpCell) > minHeroDist) &&
                                        (world.manhattanDistance(blasterCells[1], tmpCell) > minHeroDist)) {
                                    closest = tmpCell;
                                }
                                else {
                                    if ((!tmpCell.equals(blasterCells[0])) &&
                                            (!tmpCell.equals(blasterCells[1])) &&
                                            (world.manhattanDistance(blasterPlaces[3], tmpCell) > minHeroDist) &&
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
                if (distance > movePhases * 2) {
                    blasterCells[2] = null;
                    found = true;
                }
                else {
                    ++distance;
                }
            }
        }
        return blasterCells;
    }

}