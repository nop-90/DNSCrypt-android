package net.nop_90.dnscrypt;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;

import com.stericson.RootTools.RootTools;
import com.stericson.RootTools.exceptions.RootDeniedException;
import com.stericson.RootTools.execution.Command;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import au.com.bytecode.opencsv.*;


public class MainActivity extends ActionBarActivity {
    private String server_selected = null;
    private Context context;
    private ApplicationInfo info;
    private static SharedPreferences prefs;
    private String dns_default, dns_default2;

    private void copyAssets(String filename) {
        AssetManager assetManager = getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(filename);
            File outFile = new File(getApplicationInfo().dataDir+"/files/",filename);
            out = new FileOutputStream(outFile);
            copyFile(in, out);
        } catch(IOException e) {
            Log.e("tag", "Failed to copy asset file: " + filename, e);
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.context = getApplicationContext();
        this.info = getApplicationInfo();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        final List<String> list_files = new ArrayList<>();
        list_files.add("dnscrypt-proxy");
        list_files.add("hostip");
        list_files.add("libsodium.so");
        list_files.add("dnscrypt-resolvers.csv");
        for (String text_exists : list_files) {
            File file = new File(info.dataDir+"/files/"+text_exists);
            if (!file.exists()) {
                copyAssets(text_exists);
            }
            else {
                Log.i("FILE","File "+text_exists+" exists");
            }
        }
        sendCommand("chmod -R 0777 "+info.dataDir+"/files/");
        AssetManager assetManager = getAssets();
        final ListView dns_list = (ListView) findViewById(R.id.servers_list);
        final ArrayList<HashMap<String, Object>> data = new ArrayList<>();
        try {
            InputStream csvStream = assetManager.open("dnscrypt-resolvers.csv");
            InputStreamReader csvStreamReader = new InputStreamReader(csvStream);
            CSVReader csvReader = new CSVReader(csvStreamReader);
            String[] line;

            // throw away the header
            csvReader.readNext();

            while ((line = csvReader.readNext()) != null) {
                HashMap<String, Object> texts = new HashMap<>(2);
                texts.put("server_name",line[0]);
                texts.put("name", line[1]);
                texts.put("location", line[3]);
                data.add(texts);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        for (HashMap<String, Object> m :data) m.put("checked", false);
        final SimpleAdapter adapter = new SimpleAdapter(this, data,
                R.layout.simple_list_item_2_single_choice,
                new String[] {"name", "location","checked"},
                new int[] {android.R.id.text1,
                        android.R.id.text2,
                        R.id.radio});
        dns_list.setAdapter(adapter);

        dns_list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                HashMap<String, String> obj;

                LinearLayout item_view = (LinearLayout) view;
                RadioButton itemcheck = (RadioButton) item_view
                        .findViewById(R.id.radio);

                itemcheck.setChecked(true);
                if (!itemcheck.isChecked()) {
                    for (HashMap<String, Object> m : data) m.put("checked", false);

                    data.get(position).put("checked", true);
                    adapter.notifyDataSetChanged();
                }
                obj = (HashMap<String, String>) parent.getAdapter().getItem(position);
                setServerSelected(obj.get("server_name"));
            }
        });

        // Set Item at pos checked : dns_list.setItemChecked(pos, true);
        Switch onoff = (Switch) findViewById(R.id.activation);
        onoff.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (getServerSelected() != null) {
                        // Log.d("Server Selected",getServerSelected());
                        MainActivity.this.startDNS();
                    } else {
                        AlertDialog.Builder server_alert = new AlertDialog.Builder(MainActivity.this);
                        server_alert.setMessage("You haven't selected a server");
                        server_alert.setTitle("DNSCrypt");
                        server_alert.setPositiveButton("Ok",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();
                                    }
                                });
                        server_alert.create().show();
                        buttonView.setChecked(false);
                    }
                } else {
                    MainActivity.this.stopDNS();
                }
            }
        });
    }
    public void stopDNS() {
        TextView listen_text = (TextView) findViewById(R.id.address);
        listen_text.setText("éteint");
        listen_text.setTextColor(Color.RED);
        sendCommand("killall dnscrypt-crypt");
        sendCommand("ndc resolver flushif wlan0");
        sendCommand("ndc resolver flushdefaultif");
        sendCommand("ndc resolver setifdns wlan0 "+dns_default+" "+dns_default2);
        sendCommand("ndc resolver setdefaultif wlan0");
    }
    public void startDNS() {
        Log.d("Settings status", prefs.getBoolean("boot", false) + " " + prefs.getString("secondary_dns", "not set"));
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("dns_default",getServerSelected());
        editor.commit();
        sendCommand("LD_LIBRARY_PATH=\"" + info.dataDir + "/files/\" " + info.dataDir + "/files/dnscrypt-proxy -a 127.0.0.1:53 -l " + info.dataDir + "/files/log -L " + info.dataDir + "/files/dnscrypt-resolvers.csv -R " + getServerSelected());
        WifiManager wifi = (WifiManager) getSystemService(WIFI_SERVICE);
        DhcpInfo info = wifi.getDhcpInfo();
        dns_default = intToIp(info.dns1);
        dns_default2 = intToIp(info.dns2);
        sendCommand("ndc resolver flushif wlan0");
        sendCommand("ndc resolver flushdefaultif");
        String secondary_dns = prefs.getString("secondary_dns","");
        if (secondary_dns.equals("")) {
            sendCommand("ndc resolver setifdns wlan0 127.0.0.1 "+dns_default);
        }
        else {
            sendCommand("ndc resolver setifdns wlan0 127.0.0.1 "+secondary_dns);
        }
        sendCommand("ndc resolver setdefaultif wlan0");
        TextView listen_text = (TextView) findViewById(R.id.address);
        listen_text.setText("127.0.0.1:53");
        listen_text.setTextColor(Color.BLUE);
    }
    public String getServerSelected() {
        return server_selected;
    }
    public void setServerSelected(String server_name) {
        this.server_selected = server_name;
    }
    public String intToIp(int i) {
        return (i & 0xFF) + "." +
                ((i >> 8 ) & 0xFF) + "." +
                ((i >> 16) & 0xFF) + "." +
                ((i >> 24) & 0xFF);
    }
    public void sendCommand(String commands) {
        Command command = new Command(0, commands)
        {
            @Override
            public void output(int i, String s) {
                Log.i("SHELL output",s);
            }
            @Override
            public void commandCompleted(int arg0, int arg1) {
            }

            @Override
            public void commandOutput(int arg0, String arg1) {
            }

            @Override
            public void commandTerminated(int arg0, String arg1) {

            }
        };

        try {
            RootTools.getShell(true).add(command);
            while (!command.isFinished())
            {
                try
                {
                    Thread.sleep(50);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }

            }
        } catch (TimeoutException | RootDeniedException | IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                Intent intent_set = new Intent(context, Parameters.class);
                startActivity(intent_set);
                return true;
            case R.id.action_showlog:
                Intent intent_log = new Intent(context, LogView.class);
                startActivity(intent_log);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
