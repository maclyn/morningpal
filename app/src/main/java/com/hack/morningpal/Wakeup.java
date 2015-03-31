package com.hack.morningpal;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;

import jp.wasabeef.picasso.transformations.BlurTransformation;
import jp.wasabeef.picasso.transformations.gpu.BrightnessFilterTransformation;

public class Wakeup extends ActionBarActivity implements SensorEventListener {
    private static final String TAG = "Wakeup";
    List<UpdateItem> updates;
    List<String> handledPackages;
    UpdatePageAdapter upa;

    View splash;
    TextView goodDay;
    View start;
    Button manageModules;
    Button quietStart;

    TextToSpeech tts;
    boolean hasInit = false;
    boolean isQuiet = false;

    BroadcastReceiver br;

    ViewPager pager;

    //Manage proximity sensor
    SensorManager sm;
    Sensor sensor;
    long downTime = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handledPackages = new ArrayList<>();
        setContentView(R.layout.activity_wakeup);
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS){
                    hasInit = true;
                }
            }
        });
        tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
            @Override
            public void onUtteranceCompleted(String utteranceId) {
                Wakeup.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Speech has completed; move forward");
                        stepForward();
                    }
                });
            }
        });

        updates = new ArrayList<>();

        splash = findViewById(R.id.splash);
        goodDay = (TextView) splash.findViewById(R.id.goodDay);
        start = splash.findViewById(R.id.letsGo);
        manageModules = (Button) splash.findViewById(R.id.manageModules);
        quietStart = (Button) splash.findViewById(R.id.quietDay);

        start.setVisibility(View.INVISIBLE);
        quietStart.setVisibility(View.INVISIBLE);

        //Get time of day
        Calendar c = new GregorianCalendar();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        if(hour < 4){
            goodDay.setText(R.string.good_night);
        } else if (hour < 12) {
            goodDay.setText(R.string.good_morning);
        } else if (hour < 18) {
            goodDay.setText(R.string.good_afternoon);
        } else if (hour < 24) {
            goodDay.setText(R.string.good_evening);
        }

        //Start on tap
        splash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isQuiet = false;
                startProcess();
            }
        });

        //Start quiet mode on tap
        quietStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isQuiet = true;
                startProcess();
            }
        });

        //Manage modules
        manageModules.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manageModules();
            }
        });

        pager = (ViewPager) findViewById(R.id.pager);
        pager.setVisibility(View.GONE);
        upa = new UpdatePageAdapter(getSupportFragmentManager());
        pager.setAdapter(upa);
        pager.setPageTransformer(true, new DepthPageTransformer());
        pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        br = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Found some new data!");
                try {
                    if(!intent.hasExtra("sender_package")) return;
                    if(handledPackages.contains(intent.getStringExtra("sender_package"))){
                        return;
                    }

                    UpdateItem ui = new UpdateItem(context, intent);
                    handledPackages.add(intent.getStringExtra("sender_package"));
                    Log.d(TAG, "Adding update from: " + intent.getStringExtra("sender_package"));
                    updates.add(ui);
                    upa.notifyDataSetChanged();
                    Log.d(TAG, "New size of updates: " + updates.size());

                    //If first one, show start controls
                    if(updates.size() == 1){
                       start.setVisibility(View.VISIBLE);
                       quietStart.setVisibility(View.VISIBLE);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Error! Message: " + e.getMessage());
                    if(intent.hasExtra("sender_package")){
                        try {
                            Log.d(TAG, "Error from package: " + intent.getStringExtra("sender_package"));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Constants.FOUND_DATA_INTENT);

        //Register when we get new data
        registerReceiver(br, intentFilter);

        //Ask for data
        Intent getData = new Intent();
        getData.setAction(Constants.WAKEUP_INTENT);
        sendBroadcast(getData);

        //Set up proximity sensor
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    }

    private void stepForward() {
        UpdateFragment uf = (UpdateFragment) upa.getItem(pager.getCurrentItem());
        if(uf != null){
            uf.moveDown();
        }
    }

    public void haltSpeech(){
        tts.stop();
        isQuiet = true;
    }

    private void manageModules() {
        //Show dialog for managing modules
        new MaterialDialog.Builder(this)
                .title("Manage Modules")
                .positiveText("Okay")
                .items(new String[] { "test1", "test2"})
                .itemsCallbackMultiChoice(new Integer[] {0}, new MaterialDialog.ListCallbackMulti() {
                    @Override
                    public void onSelection(MaterialDialog materialDialog, Integer[] integers, CharSequence[] charSequences) {
                        for(int i : integers){
                            Toast.makeText(Wakeup.this, "Index: " + i, Toast.LENGTH_SHORT).show();
                        }
                    }
                }).show();
    }

    private void startProcess() {
        if(updates.size() > 0) {
            Log.d(TAG, "Start update; animating splash away");
            ObjectAnimator oa = ObjectAnimator.ofFloat(pager, "alpha", 0.0f, 1.0f);
            ObjectAnimator oa2 = ObjectAnimator.ofFloat(splash, "alpha", 1.0f, 0.0f);
            AnimatorSet as = new AnimatorSet();
            as.setDuration(100l);
            as.play(oa).with(oa2);
            as.start();
            pager.setVisibility(View.VISIBLE);

            if(!isQuiet){
                upa.readout();
            }
        } else {
            Log.d(TAG, "No updates; animating splash away");
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        this.unregisterReceiver(br);
        if(tts != null && hasInit) tts.shutdown();
    }

    @Override
    protected void onResume(){
        super.onResume();
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
        pager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getSupportActionBar().hide();
    }

    @Override
    protected void onPause(){
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(updates.size() == 0) return;

        if(event.values[0] < sensor.getMaximumRange()){ //Near
            downTime = System.currentTimeMillis();
        } else { //Far
            long diff = System.currentTimeMillis() - downTime;
            if(downTime == -1) return; //We need a near first

            if(diff < 250l) { //Swipe
                Log.d(TAG, "Moving right");
                moveRight();
            }

            downTime = -1;
        }
    }

    private void moveRight() {
        Log.d(TAG, "Move right called");
        if (pager.getCurrentItem() < updates.size()-1) { //Is there room?
            pager.setCurrentItem(pager.getCurrentItem() + 1, true);
            if(!isQuiet) upa.readout();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //We ignore this event; boo hoo if this is the case
    }

    private class UpdatePageAdapter extends FragmentPagerAdapter {
        private HashMap<UpdateItem, Fragment> map;

        public UpdatePageAdapter(FragmentManager fm) {
            super(fm);
            map = new HashMap<>();
        }

        @Override
        public Fragment getItem(int position) {
            if(map.containsKey(updates.get(position))){
                return map.get(updates.get(position));
            } else {
                UpdateFragment ua = new UpdateFragment();
                Bundle b = new Bundle();
                b.putInt("position", position);
                ua.setArguments(b);
                map.put(updates.get(position), ua);
                return ua;
            }
        }

        @Override
        public int getCount() {
            return updates.size();
        }

        //Handle proper speaking
        public void readout() {
            Log.d(TAG, "Reading out...");
            //Read phrase if were at index 0; otherwise read something else
            tts.stop();
            if(getItem(pager.getCurrentItem()) != null) {
                int depth = ((UpdateFragment)getItem(pager.getCurrentItem())).depth;
                PageDescription current = updates.get(pager.getCurrentItem()).getPages().get(depth);
                String utterance = current.getPhrase();

                if(utterance != null && !isQuiet) {
                    utterance = utterance.trim();
                    HashMap<String, String> params = new HashMap<>();
                    params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, String.valueOf(100));
                    tts.speak(utterance, TextToSpeech.QUEUE_FLUSH, params);
                } else {
                    Log.d(TAG, "Utterance was null");
                }
            } else {
                Log.d(TAG, "Error: null");
            }
        }
    }

    public class UpdateFragment extends Fragment {
        UpdateItem ui;
        View mainLayout;
        TextView title;
        TextView description;
        ImageView icon;
        View background;
        TextView slides;

        int depth = 0;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_update, container, false);
        }

        @Override
        public void onActivityCreated(@Nullable Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            int position = getArguments().getInt("position");
            ui = updates.get(position);

            //Set parts
            mainLayout = getView().findViewById(R.id.mainLayout);
            title = (TextView) getView().findViewById(R.id.title);
            description = (TextView) getView().findViewById(R.id.description);
            icon = (ImageView) getView().findViewById(R.id.icon);
            background = getView().findViewById(R.id.background);
            slides = (TextView) getView().findViewById(R.id.moreIndicator);

            ImageView backgroundImage = (ImageView) getView().findViewById(R.id.backgroundImage);

            int width = backgroundImage.getWidth();
            int height = backgroundImage.getHeight();
            if(width <= 0) width = 1000;
            if(height <= 0) height = 1500;

            title.setText(ui.getPages().get(0).getTitle());
            description.setText(ui.getPages().get(0).getDescription());
            icon.setImageDrawable(ui.getPages().get(0).getIcon());
            if(ui.getBackgroundUrl() != null){
                Picasso.with(this.getActivity())
                        .load(ui.getBackgroundUrl())
                        .resize(width, height)
                        .centerCrop()
                        .transform(new BlurTransformation(this.getActivity()))
                        .transform(new BrightnessFilterTransformation(this.getActivity(), 0.25f))
                        .into(backgroundImage, new Callback() {
                            @Override
                            public void onSuccess() {
                                //Log.d(TAG, "Successful loading image!");
                            }

                            @Override
                            public void onError() {
                                //Log.d(TAG, "Failed loading image :(");
                            }
                        });
            }

            if(ui.getPages().size() > 0){
                slides.setText("+" + (ui.getPages().size()-1) + " more slides");
            } else {
                slides.setText("No more slides");
            }

            backgroundImage.setOnTouchListener(new TouchDetector());
        }

        public void moveDown(){
            depth++;

            if(depth > ui.getPages().size()-1){ //Bad state
                //Move right
                depth--;
                ((Wakeup)this.getActivity()).moveRight();
            } else { //Read more info
                Log.d(TAG, "Moving views down");
                final String titleText = ui.getPages().get(depth).getTitle();
                final String descriptionText = ui.getPages().get(depth).getDescription();

                //Animate to next state
                //Translate the thing off
                background.getHeight();
                mainLayout.getHeight();
                ObjectAnimator oa = ObjectAnimator.ofFloat(mainLayout, "translationY", -((background.getHeight() + mainLayout.getHeight()) / 2));
                ObjectAnimator a1 = ObjectAnimator.ofFloat(mainLayout, "alpha", 0);
                AnimatorSet as = new AnimatorSet();
                as.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        title.setText(titleText);
                        description.setText(descriptionText);

                        if(depth < ui.getPages().size()-1){
                            slides.setText("+" + (ui.getPages().size()-1-depth) + " more slides");
                        } else {
                            slides.setText("No more slides");
                        }

                        //Jump up; translate back on
                        mainLayout.setTranslationY( (background.getHeight()/2) + (mainLayout.getHeight() / 2) );

                        ObjectAnimator oa2 = ObjectAnimator.ofFloat(mainLayout, "translationY", 0);
                        ObjectAnimator a2 = ObjectAnimator.ofFloat(mainLayout, "alpha", 1);
                        AnimatorSet as2 = new AnimatorSet();
                        as2.setDuration(200);
                        as2.play(oa2).with(a2);
                        as2.start();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
                as.setDuration(200).play(a1).with(oa);
                as.start();

                if(!isQuiet) upa.readout();
            }
        }

        public void moveUp(){
            depth--;

            if(depth < 0){ //Bad state
                depth = 0;
                return; //Don't do anything
            } else { //Read more info
                Log.d(TAG, "Moving views down");
                final String titleText = ui.getPages().get(depth).getTitle();
                final String descriptionText = ui.getPages().get(depth).getDescription();

                //Animate to next state
                //Translate the thing off
                background.getHeight();
                mainLayout.getHeight();
                ObjectAnimator oa = ObjectAnimator.ofFloat(mainLayout, "translationY", (background.getHeight() +
                        mainLayout.getHeight()) / 2);
                ObjectAnimator a1 = ObjectAnimator.ofFloat(mainLayout, "alpha", 0);
                AnimatorSet as = new AnimatorSet();
                as.addListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        title.setText(titleText);
                        description.setText(descriptionText);

                        if(depth < ui.getPages().size()-1){
                            slides.setText("+" + (ui.getPages().size()-1-depth) + " more slides");
                        } else {
                            slides.setText("No more slides");
                        }

                        //Jump up; translate back on
                        mainLayout.setTranslationY(-((background.getHeight() + mainLayout.getHeight()) / 2));

                        ObjectAnimator oa2 = ObjectAnimator.ofFloat(mainLayout, "translationY", 0);
                        ObjectAnimator a2 = ObjectAnimator.ofFloat(mainLayout, "alpha", 1);
                        AnimatorSet as2 = new AnimatorSet();
                        as2.setDuration(200);
                        as2.play(oa2).with(a2);
                        as2.start();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                    }

                    @Override
                    public void onAnimationRepeat(Animator animation) {
                    }
                });
                as.setDuration(200).play(a1).with(oa);
                as.start();
            }
        }

        public UpdateFragment(){
        }

        public class TouchDetector implements View.OnTouchListener, GestureDetector.OnGestureListener {
            GestureDetectorCompat gc;

            public TouchDetector(){
                Log.d(TAG, "Instantiating TouchDetector");
                UpdateFragment.this.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gc = new GestureDetectorCompat(UpdateFragment.this.getActivity(), TouchDetector.this);
                    }
                });
            }

            @Override
            public boolean onDown(MotionEvent e) {
                Log.d(TAG, "onDown");
                return true;
            }

            @Override
            public void onShowPress(MotionEvent e) {
                Log.d(TAG, "onShowPress");
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                Log.d(TAG, "onSingleTapUp");
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                Log.d(TAG, "onLongPress");
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                Log.d(TAG, "onFling: " + velocityX + " " + velocityY);
                if(velocityY < 0){
                    UpdateFragment.this.moveDown();
                } else if (velocityY > 0) {
                    UpdateFragment.this.moveUp();
                }
                return true;
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(gc != null){
                    ((Wakeup)UpdateFragment.this.getActivity()).haltSpeech(); //Stop speech as soon as you touch
                    return gc.onTouchEvent(event);
                }
                return true;
            }
        }
    }
}
