package client;

import client.model.*;
import client.model.Map;

import java.util.*;

public class AI {

    private Random random = new Random();
    private Map map;
    private Cell[] objCells;
    private HashMap<HeroName, Boolean> isStayingThisPhase = new HashMap<>();
    private HashMap<HeroName, Boolean> needsHealing = new HashMap<>();

    public void preProcess(World world) {
        System.out.println("<-- pre process -->");
        map = world.getMap();
        objCells = map.getObjectiveZone();
        for (HeroName heroName : HeroName.values()) {
            isStayingThisPhase.put(heroName, true);
            needsHealing.put(heroName, false);
        }
    }

    public void pickTurn(World world) {
        System.out.println("<-- pick -->");
        world.pickHero(HeroName.values()[world.getCurrentTurn()]);
    }

    public void moveTurn(World world) {

        System.out.println("\n<-- move -->");
        Hero[] myHeroes = world.getMyHeroes();

        for (Hero myHero : myHeroes) {
            HeroName myHeroName = myHero.getName();
            Cell myHeroCell = myHero.getCurrentCell();

            /* < 0. FIRST MOVE PHASE > */

            if (world.getMovePhaseNum() == 0) {
                if ((myHero.getRemRespawnTime() == 0) && (getHPPercentage(myHero) <= 60)) {
                    needsHealing.put(myHeroName, true);
                } else {
                    needsHealing.put(myHeroName, false);
                }
            }

            /* < / 0. FIRST MOVE PHASE > */

            //skip hero if dead:
            if (myHero.getRemRespawnTime() != 0) {
                continue;
            }

            System.out.print("Selected Hero: " + myHero.getName() + " ");

            /* < 1. FIND DESTINATION > */


            //get destination cell:
            Cell destCell = getDestCell(world, myHero, new HashSet<>());

            //TODO: Check for Move-Away tactic. Changes the destCell if enemy is close but f.Hero wants to stay!

            //if other friendly hero is staying in destCell and (not going to move?):
            HashSet<Cell> blockedCells = new HashSet<>();
            int tries = 3;
            while (tries >= 1 && isDestCellAlreadyFilled(world, destCell, myHero)) {
                blockedCells.add(destCell);
                destCell = getDestCell(world, myHero, blockedCells);
                if (destCell.equals(myHeroCell)) {
                    break;
                }
                tries--;
            }

            if (destCell.equals(myHeroCell)) {
                isStayingThisPhase.put(myHeroName, true);
                System.out.println("Staying");
                continue;
            }

            //announce f.Hero won't stay:
            isStayingThisPhase.put(myHeroName, false);

            /* < / 1. FIND DESTINATION > */
            /* < 2. GET DIRECTION TO DEST > */

            //create a blocked cells list for where other f.Heroes are staying:
            ArrayList<Cell> blockedCellsList = new ArrayList<>();
            for (Hero h : myHeroes) {
                if (h.equals(myHero)) {
                    continue;
                }
                //only if staying in this phase, add to blockedList:
                if (isStayingThisPhase.get(h.getName())) {
                    blockedCellsList.add(h.getCurrentCell());
                }
            }
            //get directions:
            Direction[] dirs = world.getPathMoveDirections(myHeroCell, destCell, blockedCellsList);

            /* < / 2. GET DIRECTION TO DEST > */
            /* < 3. MOVE > */

            if (dirs.length == 0) {
                System.out.println("No direction");
                isStayingThisPhase.put(myHeroName, true);
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

        //Do nothing when enemy not visible:
        if (enemies.length == 0) {
            return;
        }

        for (Hero myHero : myHeroes) {

            //skip hero if dead:
            if (myHero.getRemRespawnTime() != 0) {
                continue;
            }

            System.out.print("Selected Hero: " + myHero.getName() + " ");

            /* < INIT > */
            HashMap<HeroName, Hero> neighborFriends = getNeighborFriends(world, myHero);
            HashMap<HeroName, Hero> neighborEnemies = getNeighborEnemies(world, myHero);
            HashMap<HeroName, Hero> visibleEnemies = getVisibleEnemies(world, myHero);
            //cells of other f.Heroes:
            ArrayList<Cell> myOtherHeroesCells = getMyOtherHeroesCells(world, myHero);
            //dodgeable Cells:
            ArrayList<Cell> dodgeableCells = null;

            if (myHero.getDodgeAbilities()[0].isReady()) {
                int dodgeRange;
                if (myHero.getName().equals(HeroName.SENTRY)) {
                    dodgeRange = 3;
                } else if (myHero.getName().equals(HeroName.HEALER)) {
                    dodgeRange = 4;
                } else if (myHero.getName().equals(HeroName.BLASTER)) {
                    dodgeRange = 4;
                } else {
                    dodgeRange = 2;
                }
                dodgeableCells = getDodgeableCells(myHero, dodgeRange, myOtherHeroesCells);
            }

            //Healer zone: (if hero is healer, ability is ready, and others need healing)
            HashMap<HeroName, Hero> friendsInHealerZone = null;
            if (myHero.getName().equals(HeroName.HEALER) &&
                    myHero.getAbility(AbilityName.HEALER_HEAL).isReady() &&
                    needsHealing.containsValue(Boolean.TRUE)) {
                friendsInHealerZone = getFriendsInHealerZone(world, myHero, 4);
            }

            Hero targetHero = null;
            Cell targetCell = null;
            /* < / INIT > */

            /* SENTRY */
            if (myHero.getName().equals(HeroName.SENTRY)) {
                Ability laserAttack = myHero.getAbility(AbilityName.SENTRY_RAY);
                Ability dodgeAbility = myHero.getAbility(AbilityName.SENTRY_DODGE);

                if (!visibleEnemies.isEmpty()) {

                    //Dodge if enemy close:
                    if (!neighborEnemies.isEmpty() && dodgeAbility.isReady() &&
                            (dodgeableCells != null && !dodgeableCells.isEmpty())) {

                        targetCell = dodgeableCells.get(random.nextInt(dodgeableCells.size()));
                        if (targetCell != null) {
                            world.castAbility(myHero, dodgeAbility, targetCell);
                            System.out.println("Dodged away");
                        }
                    }

                    //Laser-Attack:
                    else if (laserAttack.isReady()) {
                        if (visibleEnemies.containsKey(HeroName.SENTRY)) {
                            targetHero = visibleEnemies.get(HeroName.SENTRY);
                        } else if (visibleEnemies.containsKey(HeroName.BLASTER)) {
                            targetHero = visibleEnemies.get(HeroName.BLASTER);
                        } else if (visibleEnemies.containsKey(HeroName.HEALER)) {
                            targetHero = visibleEnemies.get(HeroName.HEALER);
                        } else {
                            targetHero = visibleEnemies.get(HeroName.GUARDIAN);
                        }

                        world.castAbility(myHero, laserAttack, targetHero.getCurrentCell());
                        System.out.println("Laser-Attacked");
                    }
                }

                //NO Simple-Attack FOR SENTRY
            }

            /* BLASTER */
            else if (myHero.getName().equals(HeroName.BLASTER)) {
                Ability bombAttack = myHero.getAbility(AbilityName.BLASTER_BOMB);
                Ability simpleAttack = myHero.getAbility(AbilityName.BLASTER_ATTACK);
                Ability dodgeAbility = myHero.getAbility(AbilityName.BLASTER_DODGE);

                //Dodge if enemy close:
                if (!neighborEnemies.isEmpty() && dodgeAbility.isReady() &&
                        (dodgeableCells != null && !dodgeableCells.isEmpty())) {

                    targetCell = dodgeableCells.get(random.nextInt(dodgeableCells.size()));
                    if (targetCell != null) {
                        world.castAbility(myHero, dodgeAbility, targetCell);
                        System.out.println("Dodged away");
                    }
                }

                else if (bombAttack.isReady()) {
                    //Find the best cell to throw the bomb:
                    targetCell = getClosestCellToBomb(world, myHero);

                    if (targetCell != null) {
                        world.castAbility(myHero, bombAttack, targetCell);
                        System.out.println("Bomb-Attacked");
                    }
                }

                //Simple-Attack neighbors:
                else if (!neighborEnemies.isEmpty() && simpleAttack.isReady()) {

                    if (neighborEnemies.containsKey(HeroName.SENTRY)) {
                        targetCell = neighborEnemies.get(HeroName.SENTRY).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.BLASTER)) {
                        targetCell = neighborEnemies.get(HeroName.BLASTER).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.HEALER)) {
                        targetCell = neighborEnemies.get(HeroName.HEALER).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.GUARDIAN)) {
                        targetCell = neighborEnemies.get(HeroName.GUARDIAN).getCurrentCell();
                    }

                    world.castAbility(myHero, simpleAttack, targetCell);
                    System.out.println("Simple-Attacked");
                }

            }

            /* HEALER */
            else if (myHero.getName().equals(HeroName.HEALER)) {
                Ability healingAbility = myHero.getAbility(AbilityName.HEALER_HEAL);
                Ability simpleAttack = myHero.getAbility(AbilityName.HEALER_ATTACK);
                Ability dodgeAbility = myHero.getAbility(AbilityName.HEALER_DODGE);

                //Dodge if enemy close:
                if (!neighborEnemies.isEmpty() && dodgeAbility.isReady() &&
                        (dodgeableCells != null && !dodgeableCells.isEmpty())) {

                    targetCell = dodgeableCells.get(random.nextInt(dodgeableCells.size()));
                    if (targetCell != null) {
                        world.castAbility(myHero, dodgeAbility, targetCell);
                        System.out.println("Dodged away");
                    }
                }
                //Heals the one who needs the most:
                else if (friendsInHealerZone != null) {

                    //A good doctor cures himself first!
                    if (friendsInHealerZone.containsKey(HeroName.HEALER) &&
                            needsHealing.get(HeroName.HEALER)) {
                        targetHero = myHero;
                    }
                    else if (friendsInHealerZone.containsKey(HeroName.SENTRY) &&
                            needsHealing.get(HeroName.SENTRY)) {
                        targetHero = friendsInHealerZone.get(HeroName.SENTRY);
                    }
                    else if (friendsInHealerZone.containsKey(HeroName.BLASTER) &&
                            needsHealing.get(HeroName.BLASTER)) {
                        targetHero = friendsInHealerZone.get(HeroName.BLASTER);
                    }
                    else if (friendsInHealerZone.containsKey(HeroName.GUARDIAN) &&
                            needsHealing.get(HeroName.GUARDIAN)) {
                        targetHero = friendsInHealerZone.get(HeroName.GUARDIAN);
                    }

                    if (targetHero != null) {
                        world.castAbility(myHero, healingAbility, targetHero.getCurrentCell());
                        System.out.println("Healed(" + targetHero.getName() +")");
                    }
                }
                //Simple-Attack neighbors:
                else if (!neighborEnemies.isEmpty()) {
                    if (neighborEnemies.containsKey(HeroName.SENTRY)) {
                        targetCell = neighborEnemies.get(HeroName.SENTRY).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.BLASTER)) {
                        targetCell = neighborEnemies.get(HeroName.BLASTER).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.HEALER)) {
                        targetCell = neighborEnemies.get(HeroName.HEALER).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.GUARDIAN)) {
                        targetCell = neighborEnemies.get(HeroName.GUARDIAN).getCurrentCell();
                    }

                    world.castAbility(myHero, simpleAttack, targetCell);
                    System.out.println("Simple-Attacked");
                }
            }

            /* GUARDIAN */
            else if (myHero.getName().equals(HeroName.GUARDIAN)) {

                //Dodge to the closest enemy if no e.Hero is a neighbor - except e.Guardian:
                if (neighborEnemies.isEmpty()) {
                    HashSet<HeroName> blockedEnemies = new HashSet<>();
                    blockedEnemies.add(HeroName.GUARDIAN);
                    targetHero = findClosestEnemyCell(world, myHero, blockedEnemies);

                    if (targetHero != null) {
                        world.castAbility(myHero, AbilityName.GUARDIAN_DODGE, targetHero.getCurrentCell());
                        System.out.println("Dodged near enemy");
                    }
                }
                //Simple-Attack neighbor priority:
                else {
                    if (neighborEnemies.containsKey(HeroName.SENTRY)) {
                        targetCell = neighborEnemies.get(HeroName.SENTRY).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.BLASTER)) {
                        targetCell = neighborEnemies.get(HeroName.BLASTER).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.HEALER)) {
                        targetCell = neighborEnemies.get(HeroName.HEALER).getCurrentCell();
                    }
                    else if (neighborEnemies.containsKey(HeroName.GUARDIAN)) {
                        targetCell = neighborEnemies.get(HeroName.GUARDIAN).getCurrentCell();
                    }
                    world.castAbility(myHero, AbilityName.GUARDIAN_ATTACK, targetCell);
                    System.out.println("Simple-Attacked");
                }
            }

        }
    }




    private Cell getDestCell(World world, Hero myHero, HashSet<Cell> blockedCell) {

        Cell destCell = null;

        /* SENTRY */
        if (myHero.getName().equals(HeroName.SENTRY)) {
            //stop if enemy is visible to his vision:
            for (Hero enemy : world.getOppHeroes()) {
                if (isEnemyVisibleToHero(world, enemy, myHero)) {
                    destCell = myHero.getCurrentCell();
                    return destCell;
                }
            }
        }

        /* BLASTER */
        else if (myHero.getName().equals(HeroName.BLASTER)) {
            destCell = getClosestObjCell(world, map.getOppRespawnZone()[0], blockedCell);
            return destCell;
        }

        /* GUARDIAN */
        else if (myHero.getName().equals(HeroName.GUARDIAN)) {
            //go to closest e.Hero cell except for e.Guardian:
            for (Hero enemy : world.getOppHeroes()) {
                if (isEnemyVisible(enemy) && !enemy.getName().equals(HeroName.GUARDIAN)) {
                    if (destCell == null) {
                        destCell = enemy.getCurrentCell();
                    }
                    else {
                        //is it closer?
                        if (world.manhattanDistance(myHero.getCurrentCell(), enemy.getCurrentCell()) <
                                world.manhattanDistance(myHero.getCurrentCell(), destCell)) {
                            destCell = enemy.getCurrentCell();
                        }
                    }
                }
            }
            if (destCell != null) {
                return destCell;
            }
        }

        /* HEALER */
        else if (myHero.getName().equals(HeroName.HEALER)) {
            //find cell of friend who needs healing the most:
            Hero needsHealTheMost = null;
            for (Hero friend : world.getMyHeroes()) {

                if (needsHealing.get(friend.getName())) {
                    if (needsHealTheMost == null) {
                        needsHealTheMost = friend;
                    }
                    else if (getHPPercentage(friend) < getHPPercentage(needsHealTheMost)) {
                        needsHealTheMost = friend;
                    }
                }
            }
            if (needsHealTheMost != null) {
                destCell = getClosestCellForHealer(world, myHero, needsHealTheMost, blockedCell);
                //stay:
                if (destCell.equals(myHero.getCurrentCell())) {
                    return destCell;
                }
                //move:
                else {
                    return destCell;
                }
            }

            //stay or hide - no one needs healing:
            destCell = myHero.getCurrentCell();
            return destCell;

        }

        //if enemy is not visible, move to the closest ObjectiveZone:
        destCell = getClosestObjCell(world, myHero.getCurrentCell(), blockedCell);
        return destCell;
    }

    private boolean canHeroMoveToCell(Cell cell, ArrayList<Cell> myOtherHeroesCells) {
        return !cell.isWall() && !cell.isInOppRespawnZone() && !myOtherHeroesCells.contains(cell);
    }

    private Cell getCellByDir(Cell cell, Direction dir) {
        Cell nextCell = null;
        // find the destination cell for the given cell and direction:
        switch (dir) {
            case UP:
                nextCell = map.getCell(cell.getRow(), cell.getColumn() + 1);
                break;
            case DOWN:
                nextCell = map.getCell(cell.getRow(), cell.getColumn() - 1);
                break;
            case RIGHT:
                nextCell = map.getCell(cell.getRow() + 1, cell.getColumn());
                break;
            case LEFT:
                nextCell = map.getCell(cell.getRow() - 1, cell.getColumn());
                break;
        }
        return nextCell;
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

    private HashMap<HeroName, Hero> getVisibleEnemies(World world, Hero myHero) {
        HashMap<HeroName, Hero> visibleEnemies = new HashMap<>();
        for (Hero enemy : world.getOppHeroes()) {
            if (isEnemyVisibleToHero(world, enemy, myHero)) {
                visibleEnemies.put(enemy.getName(), enemy);
            }
        }
        return visibleEnemies;
    }

    private boolean isEnemyVisible(Hero enemy) {
        return enemy.getCurrentCell().getRow() != -1;
    }

    private boolean isEnemyVisibleToHero(World world, Hero enemy, Hero myHero) {
        return world.isInVision(myHero.getCurrentCell(), enemy.getCurrentCell());
    }

    /* null if no enemy visible */
    private Hero findClosestEnemyCell(World world, Hero hero, HashSet<HeroName> blockedEnemies) {
        Hero[] enemies = world.getOppHeroes();
        Hero closestEnemy = null;
        for (Hero enemy : enemies) {
            if ((blockedEnemies.isEmpty() || !blockedEnemies.contains(enemy.getName())) && isEnemyVisible(enemy)) {
                if (closestEnemy == null) {
                    closestEnemy = enemy;
                }
                else if (world.manhattanDistance(hero.getCurrentCell(), enemy.getCurrentCell()) <
                        world.manhattanDistance(hero.getCurrentCell(), closestEnemy.getCurrentCell())) {
                    closestEnemy = enemy;
                }
            }
        }
        return closestEnemy;
    }

    private boolean isDestCellAlreadyFilled(World world, Cell destCell, Hero myHero) {
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

    private int countEnemiesAffectedByBomb(World world, Cell cell) {
        int affectedEnemies = 0;
        HashSet<Cell> affectedCells = new HashSet<>();
        for (int i = -2; i <= 2; ++i) {
            for (int j = -2; j <= 2; ++j) {
                if (map.isInMap(cell.getRow() + i, cell.getColumn() + j)) {
                    Cell affectedCell = map.getCell(cell.getRow() + i, cell.getColumn() + j);
                    affectedCells.add(affectedCell);
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

    private int getHPPercentage(Hero hero) {
        return (hero.getCurrentHP() * 100) / hero.getMaxHP();
    }

    private ArrayList<Cell> getNeighborCells(Hero hero) {
        ArrayList<Cell> neighborCells = new ArrayList<>();
        Cell cell = hero.getCurrentCell();
        for (int i = -1; i <= 1; ++i) {
            for (int j = -1; j <= 1; ++j) {
                if ( ((i != 0) && (j == 0)) || (((i == 0) && (j != 0))) ) {
                    continue;
                }
                if (map.isInMap(cell.getRow() + i, cell.getColumn() + j)) {
                    neighborCells.add(map.getCell(cell.getRow() + i, cell.getColumn() + j));
                }
            }
        }
        return neighborCells;
    }

    private HashMap<HeroName, Hero> getNeighborEnemies(World world, Hero hero) {
        HashMap<HeroName, Hero> neighborEnemies = new HashMap<>();
        Cell heroCell = hero.getCurrentCell();
        int cellRow = heroCell.getRow();
        int cellColumn = heroCell.getColumn();
        ArrayList<Cell> neighborCells = new ArrayList<>();
        neighborCells.add(map.getCell(cellRow, cellColumn));
        neighborCells.add(map.getCell(cellRow + 1, cellColumn));
        neighborCells.add(map.getCell(cellRow - 1, cellColumn));
        neighborCells.add(map.getCell(cellRow, cellColumn + 1));
        neighborCells.add(map.getCell(cellRow, cellColumn - 1));
        Hero[] enemyHeroes = world.getOppHeroes();
        // check every enemies cells to see if the hero is our hero's neighbors:
        for (Hero enemy : enemyHeroes) {
            for (Cell nc : neighborCells) {
                if (enemy.getCurrentCell().equals(nc)) {
                    neighborEnemies.put(enemy.getName(), enemy);
                }
            }
        }
        return neighborEnemies;
    }

    private HashMap<HeroName, Hero> getNeighborFriends(World world, Hero myHero) {
        HashMap<HeroName, Hero> neighborFriends = new HashMap<>();
        int cellRow = myHero.getCurrentCell().getRow();
        int cellColumn = myHero.getCurrentCell().getColumn();
        ArrayList<Cell> neighborCells = new ArrayList<>();
        neighborCells.add(map.getCell(cellRow, cellColumn));
        neighborCells.add(map.getCell(cellRow + 1, cellColumn));
        neighborCells.add(map.getCell(cellRow - 1, cellColumn));
        neighborCells.add(map.getCell(cellRow, cellColumn + 1));
        neighborCells.add(map.getCell(cellRow, cellColumn - 1));

        Hero[] friendHeroes = world.getMyHeroes();
        for (Hero friend : friendHeroes) {
            for (Cell fc : neighborCells) {
                if (friend.getCurrentCell().equals(fc)) {
                    neighborFriends.put(friend.getName(), friend);
                }
            }
        }
        return neighborFriends;
    }

    private Cell getClosestCellForHealer(World world, Hero healer, Hero destHero, HashSet<Cell> blockedCells) {
        if (healer.equals(destHero)) {
            return healer.getCurrentCell();
        }
        Cell closestHealCell = null;
        Cell destHeroCell = destHero.getCurrentCell();
        for (int i = -4; i <= 4; ++i) {
            for (int j = -4; j <= 4; ++j) {
                //if the cell exists in map:
                if (map.isInMap(destHeroCell.getRow() + i, destHeroCell.getColumn() + j)) {
                    Cell tmpCell = map.getCell(destHeroCell.getRow() + i, destHeroCell.getColumn() + j);
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
        return closestHealCell;
    }

    private Cell getClosestCellToBomb(World world, Hero blaster) {
        Cell targetCell = null;
        int maxAffected = 0;
        for (Hero enemy : world.getOppHeroes()) {
            Cell enemyCell = enemy.getCurrentCell();
            for (int i = -2; i <= 2; ++i) {
                for (int j = -2; j <= 2; ++j) {
                    //is the cell even in map??
                    if (map.isInMap(enemyCell.getRow() + i, enemyCell.getColumn() + j)) {
                        Cell aroundCell = map.getCell(
                                enemyCell.getRow() + i,
                                enemyCell.getColumn() + j
                        );
                        //is the cell reachable to bomb?
                        if (world.manhattanDistance(blaster.getCurrentCell(), aroundCell) <= 5) {
                            //is it the best target cell?
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
        return targetCell;
    }

    private HashMap<HeroName, Hero> getFriendsInHealerZone(World world, Hero healer, int range) {
        HashMap<HeroName, Hero> friendsInHealerZone = new HashMap<>();
        Cell heroCell = healer.getCurrentCell();
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                if (map.isInMap(heroCell.getRow() + i, heroCell.getColumn() + j)) {
                    Cell aroundCell = map.getCell(heroCell.getRow() + i, heroCell.getColumn() + j);
                    for (Hero hero : world.getMyHeroes()) {
                        if (hero.getCurrentCell().equals(aroundCell)) {
                            friendsInHealerZone.put(hero.getName(), hero);
                        }
                    }
                }
            }
        }
        return friendsInHealerZone;
    }

    private ArrayList<Cell> getDodgeableCells(Hero hero, int range, ArrayList<Cell> myOtherHeroesCells) {
        ArrayList<Cell> dodgeableCells = new ArrayList<>();
        Cell heroCell = hero.getCurrentCell();
        for (int i = -range; i <= range; ++i) {
            for (int j = -range; j <= range; ++j) {
                if (map.isInMap(heroCell.getRow() + i, heroCell.getColumn() + j)) {
                    Cell aroundCell = map.getCell(heroCell.getRow() + i, heroCell.getColumn() + j);
                    if (canHeroMoveToCell(aroundCell, myOtherHeroesCells)) {
                        dodgeableCells.add(aroundCell);
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

}