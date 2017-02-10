package com.mogujie.aceso.demo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Toast;
import com.android.annotations.FixMtd;

import com.mogujie.aceso.Aceso;

import java.io.File;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void fix(View view) {
        File patchFile = new File(Environment.getExternalStorageDirectory(), "fix.apk");
        if (!patchFile.exists()) {
            Toast.makeText(this, "hotfix file not exist!", Toast.LENGTH_SHORT).show();
            return;
        }
        File optDir = new File(this.getFilesDir(), "fix_opt");
        optDir.mkdirs();
        new Aceso().installPatch(optDir, patchFile);
    }

    @FixMtd
    public void test(View view) {
        Toast.makeText(this, "has been fixed !", Toast.LENGTH_SHORT).show();
    }
}
