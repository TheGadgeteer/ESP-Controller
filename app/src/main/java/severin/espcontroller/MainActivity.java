package severin.espcontroller;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.content.Intent;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Observable;
import java.util.Observer;

public class MainActivity extends AppCompatActivity implements Observer, AxisControllerListener {

    private ESP_Client client;
    private SteeringManager manager;

    private TextView buttonConnect, textStatus, textSpeed, textDir;
    private AxisController speedController, dirController;
    private Switch switchEngine, switchSpeedMode;

    private int colorConnected;
    private int colorDisconnected;
    private int colorConnecting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //set colors
        colorConnected = getResources().getColor(R.color.green, getTheme());
        colorDisconnected = getResources().getColor(R.color.red, getTheme());
        colorConnecting = getResources().getColor(R.color.darkYellow, getTheme());

        buttonConnect = findViewById(R.id.buttonConnect);
        textStatus = findViewById(R.id.textStatus);

        textSpeed = findViewById(R.id.textSpeed);
        textDir = findViewById(R.id.textDir);

        speedController = findViewById(R.id.speedSteering);
        dirController = findViewById(R.id.dirSteering);
        speedController.addAxisControllerListener(this);
        dirController.addAxisControllerListener(this);

        speedController.setMinValue(-ESP_Client.maxSpeed);
        speedController.setMaxValue(ESP_Client.maxSpeed);
        speedController.setValue(speedController.getDefaultValue());
        dirController.setValue(dirController.getDefaultValue());

        client = ESP_Client.instance();
        client.addObserver(this);

        manager = SteeringManager.instance();
        manager.setSpeedController(speedController);
        manager.setDirController(dirController);

        switchEngine = findViewById(R.id.switchEngine);
        manager.setEngineSwitch(switchEngine);
        switchSpeedMode = findViewById(R.id.switchSpeedMode);
        manager.setSpeedModeSwitch(switchSpeedMode);

        update(null, null);
    }

    @Override
    public void onBackPressed() {
        client.endConnection();
        super.onBackPressed();
    }

    public void onConnect(View view) {
        textStatus.setText("Connecting...");
        textStatus.setBackgroundColor(colorConnecting);
        buttonConnect.setVisibility(View.INVISIBLE);
        new Thread(){
            @Override
            public void run() {
                final int ret = ESP_Client.instance().startConnection();
                if (ret != 0) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            String message;
                            if (ret == 1)
                                message = "ERROR: Another client is connected to the Server";
                            else if (ret == 2)
                                message = "ERROR: The Server has another version installed";
                            else
                                message = "ERROR: Can't connect to server";
                            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    SteeringManager.instance().setEngine(false);
                }
            }
        }.start();
    }

    public void onSettings(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }



    //Listener functions

    //update Function called by ESP_Client when the connection changes
    @Override
    public void update(Observable observable,  final Object o) {
        if (client.isConnected()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textStatus.setText("Connected");
                    textStatus.setBackgroundColor(colorConnected);
                    buttonConnect.setVisibility(View.INVISIBLE);
                    switchEngine.setEnabled(true);
                }});
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (o instanceof ESP_Client.ConnectionLost) {
                        Toast.makeText(getApplicationContext(), "ERROR: Connection Lost", Toast.LENGTH_SHORT).show();
                    }
                    textStatus.setText("Not Connected");
                    textStatus.setBackgroundColor(colorDisconnected);
                    buttonConnect.setVisibility(View.VISIBLE);
                    switchEngine.setChecked(false);
                    switchEngine.setEnabled(false);
                }});
        }
    }


    //Called by speedController or dirController when a value changes
    @Override
    public void onValueChanged(AxisController l, int value) {
        if (l == speedController) {
            textSpeed.setText((100 * value / speedController.getMaxValue()) + "%");
        } else if (l == dirController) {
            float dirVal = dirController.getValue() / 100f;
            if (dirVal > 1f)
                dirVal = 2f - dirVal;
            textDir.setText(String.format("%.2f", dirVal));
        }
    }

}
