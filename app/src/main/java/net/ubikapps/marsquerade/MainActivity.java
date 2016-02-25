package net.ubikapps.marsquerade;

import android.content.Intent;
import android.os.Bundle;
import android.app.Activity;
import android.view.View;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    public void launchCardboard(View view){
        Intent intent = new Intent();
        intent.setClass(getApplicationContext(), CardboardMaskActivity.class);
        startActivity(intent);
    }

    public void launchNoCardboard(View view){
        Intent intent = new Intent();
        intent.setClass(getApplicationContext(), NoCardboardActivity.class);
        startActivity(intent);
    }
}
