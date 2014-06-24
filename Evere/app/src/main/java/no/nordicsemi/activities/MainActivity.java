package no.nordicsemi.activities;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.gsm.SmsManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.radiusnetworks.ibeacon.IBeacon;
import com.radiusnetworks.ibeacon.IBeaconConsumer;
import com.radiusnetworks.ibeacon.IBeaconManager;
import com.radiusnetworks.ibeacon.RangeNotifier;
import com.radiusnetworks.ibeacon.Region;

import org.droidparts.activity.Activity;
import org.droidparts.annotation.inject.InjectView;
import org.droidparts.model.Entity;
import org.json.JSONException;

import java.util.Collection;
import java.util.Random;

import no.nordicsemi.R;
import no.nordicsemi.actuators.Actuator;
import no.nordicsemi.actuators.HttpActuator;
import no.nordicsemi.actuators.RingerActuator;
import no.nordicsemi.adapters.LocationPuckAdapter;
import no.nordicsemi.db.LocationPuckManager;
import no.nordicsemi.models.LocationPuck;
import no.nordicsemi.triggers.LocationTrigger;


public class MainActivity extends Activity implements IBeaconConsumer {

    @InjectView
    ListView lvLocationPucks;
    private LocationPuckAdapter adapter;
    private IBeaconManager mIBeaconManager;

    @Override
    public void onPreInject() {
        super.onPreInject();
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIBeaconManager = IBeaconManager.getInstanceForApplication(this);
        mIBeaconManager.bind(this);
        adapter = new LocationPuckAdapter(this, new LocationPuckManager(this)
                .select());
        lvLocationPucks.setAdapter(adapter);

        lvLocationPucks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Entity selected = adapter.read(position);
                showLocationPuckActuatorsDialog((LocationPuck) selected);
            }
        });

        lvLocationPucks.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(getString(R.string.puck_remove))
                        .setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                adapter.delete(position);
                            }
                        })
                        .setNegativeButton(getString(R.string.abort), null);
                builder.create().show();
                return true;
            }
        });
    }

    public void locationPuckDiscovered(final IBeacon iBeacon) {
        final LocationPuck newLocationPuck = new LocationPuck(null,
                iBeacon.getMinor(),
                iBeacon.getMajor(),
                iBeacon.getProximityUuid());

        if (new LocationPuckManager(this).locationPuckExists(newLocationPuck)) {
            Toast.makeText(MainActivity.this,
                    getString(R.string.location_puck_already_paired),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final View view = getLayoutInflater().inflate(R.layout.dialog_location_puck_add, null);
        ((TextView) view.findViewById(R.id.tvLocationPuckIdentifier)).setText(newLocationPuck.getFormattedUUID());

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(view)
                .setTitle(getString(R.string.puck_discovered_dialog_title))
                .setPositiveButton(getString(R.string.accept), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String locationPuckName = ((TextView) view.findViewById(R.id
                                .etLocationPuckName)).getText().toString();

                        newLocationPuck.setName(locationPuckName);
                        adapter.create(newLocationPuck);
                    }
                })
                .setNegativeButton(getString(R.string.reject), null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showLocationPuckActuatorsDialog(LocationPuck locationPuck) {
        String[] puckActuators = getResources().getStringArray(
                new Random().nextFloat() > 0.5 ?
                        R.array.officeActuators : R.array.kitchenActuators);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View v = getLayoutInflater().inflate(R.layout.dialog_location_puck_actuations, null);

        TextView tvPuckActuatorTitle = (TextView) v.findViewById(R.id.tvPuckActuatorTitle);
        tvPuckActuatorTitle.setText("Actuators for " + locationPuck.getName());

        ListView actuatorsList = (ListView) v.findViewById(R.id.lvPuckActuators);
        ArrayAdapter<String> puckActuatorsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, puckActuators);
        actuatorsList.setAdapter(puckActuatorsAdapter);

        builder.setView(v);

        builder.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onIBeaconServiceConnect() {
        mIBeaconManager.setRangeNotifier(new RangeNotifier() {
            boolean hasEnteredOffice = false;
            boolean hasEnteredKitchen = false;

            @Override
            public void didRangeBeaconsInRegion(Collection<IBeacon> iBeacons, final Region region) {
                String regionName = region.getUniqueId();

                Actuator httpActuator = new HttpActuator();
                Actuator ringerActuator = new RingerActuator();
                for (final IBeacon iBeacon : iBeacons) {
                    switch(regionName) {
                        case "office":
                            if (iBeacon.getProximity() == IBeacon.PROXIMITY_IMMEDIATE) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        locationPuckDiscovered(iBeacon);
                                    }
                                });
                            } else if(iBeacon.getProximity() == IBeacon.PROXIMITY_NEAR) {
                                if (hasEnteredOffice || hasEnteredKitchen) {
                                    return;
                                }

                                hasEnteredOffice = true;
                                Toast.makeText(MainActivity.this, "Entered the desk area", Toast.LENGTH_SHORT).show();

                                try {
                                    httpActuator.actuate("{\"url\": \"http://dev.stianj.com:1337/message\", \"data\": \"message=Sigve is by his desk\"}");
                                    ringerActuator.actuate("{\"mode\": " + AudioManager.RINGER_MODE_NORMAL + "}");
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            } else if (iBeacon.getProximity() == IBeacon.PROXIMITY_FAR) {
                                hasEnteredOffice = false;
                            }
                            break;
                        case "kitchen":
                            if (iBeacon.getProximity() == IBeacon.PROXIMITY_IMMEDIATE) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        locationPuckDiscovered(iBeacon);
                                    }
                                });
                            } else if (iBeacon.getProximity() == IBeacon.PROXIMITY_NEAR) {
                                if (hasEnteredKitchen || hasEnteredOffice) {
                                    return;
                                }

                                hasEnteredKitchen = true;
                                Toast.makeText(MainActivity.this, "Entered the meeting room", Toast.LENGTH_SHORT).show();

                                try {
                                    httpActuator.actuate("{\"url\": \"http://dev.stianj.com:1337/message\", \"data\": \"message=Sigve is in the meeting room\"}");
                                    ringerActuator.actuate("{\"mode\":  " + AudioManager.RINGER_MODE_VIBRATE + "}");
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                                SmsManager smsManager = SmsManager.getDefault();
                                smsManager.sendTextMessage("48272582", null, "Hello, I am in the meeting room right now.", null, null);
                            } else if (iBeacon.getProximity() == IBeacon.PROXIMITY_FAR) {
                                hasEnteredKitchen = false;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        });

        try {
            mIBeaconManager.startRangingBeaconsInRegion(new Region("office",
                    "E20A39F473F54BC4A12F17D1AD07A961", 0x1337, 0x0F1C));
            mIBeaconManager.startRangingBeaconsInRegion(new Region("kitchen",
                    "E20A39F473F54BC4A12F17D1AD07A961", 0x1337, 0xC175));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}