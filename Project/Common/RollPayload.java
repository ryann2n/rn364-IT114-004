package Project.Common;

public class RollPayload extends Payload {
    private int dice, sides;

    public RollPayload(int dice, int sides) {
        this.dice = dice;
        this.sides = sides;
        setPayloadType(PayloadType.ROLL);
    }

    public int getDice() {
        return dice;
    }

    public void setDice(int dice) {
        this.dice = dice;
    }

    public int getSides() {
        return sides;
    }

    public void setSides(int sides) {
        this.sides = sides;
    }
}