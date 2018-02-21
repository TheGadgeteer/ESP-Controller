package severin.espcontroller;

import android.widget.CompoundButton;
import android.widget.Switch;

import severin.espcontroller.ESP_Client.*;

/**
 * Created by Severin on 15.12.2017.
 * Class that Listens to the Components in MainActivity,
 * calculates the speed and calls the functions from ESP_Client
 */

public class SteeringManager implements AxisControllerListener, CompoundButton.OnCheckedChangeListener {

    private float dirMaxMargin = 1f;
    private ESP_Client client = ESP_Client.instance();

    private AxisController dirController = null, speedController = null;
    private Switch engineSwitch, speedModeSwitch;

    private boolean steeringEnabled = false;

    private static SteeringManager unique;

    public static SteeringManager instance() {
        if (unique == null)
            unique = new SteeringManager();
        return unique;
    }

    private SteeringManager() {
    }

    public void setDirController(AxisController c) {
        this.dirController = c;
        c.addAxisControllerListener(this);
        c.setDefaultValue(100);
        updateDirController();
    }

    public void setSpeedController(AxisController c) {
        if(speedController != null)
            c.setValue(speedController.getValue());   //keep current Value of speedController
        this.speedController = c;
        c.addAxisControllerListener(this);
        c.setDefaultValue(0);
        c.setMinValue(-ESP_Client.maxSpeed);
        c.setMaxValue(ESP_Client.maxSpeed);
    }

    public void setEngineSwitch(Switch s) {
        this.engineSwitch = s;
        s.setOnCheckedChangeListener(this);
    }

    public void setSpeedModeSwitch(Switch s) {
        this.speedModeSwitch = s;
        s.setOnCheckedChangeListener(this);
    }

    public void setDirMaxValue(float val) {
        this.dirMaxMargin = val;
        updateDirController();
    }

    public float getDirMaxMargin() {
        return this.dirMaxMargin;
    }

    private void updateDirController() {
        dirController.setMaxValue((int)(dirMaxMargin * 100) + 100);
        dirController.setMinValue(- (int)(dirMaxMargin * 100) + 100);
    }

    private void enableSteering() {
        if (steeringEnabled)
            return;
        dirController.setValue(dirController.getDefaultValue());
        speedController.setValue(speedController.getDefaultValue());
        steeringEnabled = true;
    }

    private void disableSteering() {
        steeringEnabled = false;
    }

    public void setSpeedMode(boolean b) {
        if (b) {
            speedController.setScaleY(0.5f);
            speedController.setValue(0);
            speedController.setMaxValue(1);
            speedController.setMinValue(-1);
        } else {
            speedController.setScaleY(1);
            speedController.setMaxValue(ESP_Client.maxSpeed);
            speedController.setMinValue(-ESP_Client.maxSpeed);
        }

    }

    public void setEngine(final boolean ON) {
        new Thread() {
            public void run() {
                client.setEngines(ON);
            }
        }.start();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (compoundButton == speedModeSwitch) {
            setSpeedMode(b);
        } else if (compoundButton == engineSwitch) {
            setEngine(b);
            if (b)
                enableSteering();
            else
                disableSteering();
        }
    }

    /**
     * The dirController gives the v1/v2 difference.
     * Radius r (from middle of boat):
     *  r = d / 2 * (1 + v2/v1) / (1 - (v2/v1)    //d == margin between motors
     * Mid is v2/v1 = 1 : Radius infinite(straight line) (Pol of graph)
     * v2/v1 = -1  and 3: Boat's turning without moving in one or the other direction
     * v2/v1 = 0 : Boat's turning around one motor
     * v1 = Motor.Right, v2 = Motor.Left so for negative values("left" side of graph) the boats turns to the left
     * NOTE: Ratio must be within -1 and 3
     */
    @Override
    public void onValueChanged(AxisController l, int value) {
        System.out.println("Value speedController: " + speedController.getValue());
        if (speedController == null || dirController == null)
            return;
        int factor = speedController.getMaxValue() == 1 ? ESP_Client.maxSpeed : 1; //if speedController is in speed mode, multiply the speed
        final int speed = speedController.getValue() * factor;
        final float ratio = dirController.getValue() / 100f;
        new Thread() {
            @Override
            public void run() {
                if (ratio <= 1) { //Right Motor is faster
                    client.setSpeed(speed, Motor.RIGHT);
                    client.setSpeed((int)(speed * ratio), Motor.LEFT); //ratio MUST be >= -1
                } else { //Left Motor is Faster
                    client.setSpeed(speed, Motor.LEFT);
                    client.setSpeed((int)(speed * (2 - ratio)), Motor.RIGHT); //ratio MUST be <= 3
                }
            }
        }.start();
    }
}
