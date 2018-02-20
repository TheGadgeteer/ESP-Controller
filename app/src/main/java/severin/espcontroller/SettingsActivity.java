package severin.espcontroller;

import android.graphics.PorterDuff;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Observable;
import java.util.Observer;

public class SettingsActivity extends AppCompatActivity implements Observer, SeekBar.OnSeekBarChangeListener, TextView.OnEditorActionListener {

    private TextView editPort, editIPAddress, buttonDisconnect, textSens;
    private SeekBar barSensitivity;

    private int colorConnected, colorDisconnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        colorConnected = getResources().getColor(R.color.red, getTheme());
        colorDisconnected = getResources().getColor(R.color.darkRed, getTheme());

        editPort = findViewById(R.id.editPort);
        editIPAddress = findViewById(R.id.editIPAddress);
        buttonDisconnect = findViewById(R.id.buttonDisconnect);
        textSens = findViewById(R.id.textSens);
        barSensitivity = findViewById(R.id.barSensitivity);
        barSensitivity.setMax(100 * 2);

        barSensitivity.getProgressDrawable().setColorFilter(0xFF00A000, PorterDuff.Mode.SRC_IN);
        barSensitivity.getThumb().setColorFilter(0xFF00FF00, PorterDuff.Mode.SRC_IN);

        //add Listeners and Observers
        barSensitivity.setOnSeekBarChangeListener(this);
        editIPAddress.setOnEditorActionListener(this);
        editPort.setOnEditorActionListener(this);
        textSens.setOnEditorActionListener(this);

        ESP_Client.instance().addObserver(this);

        this.update(null, null);

        editPort.setCursorVisible(false);
        editIPAddress.setCursorVisible(false);
        textSens.setCursorVisible(false);
    }

    public void onDisconnect(View view) {
        if (ESP_Client.instance().isConnected()) {
            new Thread(new Runnable() {
                public void run() {
                    ESP_Client.instance().endConnection();
                }
            }).start();
        }

    }

    public void onReset(View view) {
        ESP_Client client = ESP_Client.instance();
        client.setIPAddress(client.defaultIPAddress);
        client.setPort(client.defaultPort);
    }


    public void onPortChanged(View view) {
        ESP_Client.instance().setPort(Integer.valueOf(editPort.getText().toString()));
        update(null, null);
    }

    public void onIPChanged(View view) {
        ESP_Client.instance().setIPAddress(editIPAddress.getText().toString());
        update(null, null);
    }

    public void onSensChanged(View view) {
        float val = Float.valueOf(textSens.getText().toString());
        SteeringManager.instance().setDirMaxValue(val);
        barSensitivity.setProgress((int)(val * 100));
    }

    @Override
    public void update(Observable observable, Object o) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ESP_Client client = ESP_Client.instance();
                editPort.setText("" + client.getPort());
                editIPAddress.setText(client.getIPAddress());
                if (client.isConnected()) {
                    buttonDisconnect.setBackgroundColor(colorConnected);
                } else {
                    buttonDisconnect.setBackgroundColor(colorDisconnected);
                }
                barSensitivity.setProgress((int)(SteeringManager.instance().getDirMaxMargin() * 100));
            }});
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        textSens.setText(String.format("%.2f", (float)i / 100));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
       SteeringManager.instance().setDirMaxValue((float)seekBar.getProgress() / 100);
    }

    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            if (textView == editPort) {
                onPortChanged(textView);
            } else if (textView == editIPAddress) {
                onIPChanged(textView);
            } else if (textView == textSens) {
                onSensChanged(textView);
            }
        }
        return false; //return false so that the keyboard will hide
    }
}
