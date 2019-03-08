package client.Salek;

import client.model.Cell;
import client.model.Hero;

import java.util.ArrayList;

public class CellAndNumAndEnemyList {

    Cell hitCell;
    Integer affectedNum;
    ArrayList<Hero> enemies;

    public CellAndNumAndEnemyList(Cell hitCell, Integer affectedNum, ArrayList<Hero> enemies) {
        this.hitCell = hitCell;
        this.affectedNum = affectedNum;
        this.enemies = enemies;
    }

    public Cell getCell() {
        return this.hitCell;
    }

    public int getAffectedNum() {
        return this.affectedNum;
    }

    public ArrayList<Hero> getEnemyList() {
        return this.enemies;
    }


}
