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
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;

import java.util.ArrayList;
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

    TextToSpeech tts;
    boolean hasInit = false;
    ViewPager pager;

    int depth = 0;
    boolean movingDown = false;

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
                        if(movingDown){
                            moveDown();
                        } else {
                            moveRight();
                        }
                    }
                });
            }
        });

        updates = new ArrayList<>();

        splash = findViewById(R.id.splash);

        pager = (ViewPager) findViewById(R.id.pager);
        upa = new UpdatePageAdapter(getSupportFragmentManager());
        pager.setAdapter(upa);
        pager.setPageTransformer(true, new DepthPageTransformer());
        pager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                Log.d(TAG, "Page selected: " + position);
                depth = 0;
                movingDown = false;
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        BroadcastReceiver br = new BroadcastReceiver() {
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
                    Log.d(TAG, "Adding update item with: " + ui.getTitle());
                    updates.add(ui);
                    upa.notifyDataSetChanged();
                    Log.d(TAG, "New size of updates: " + updates.size());

                    //If first one, fade in
                    if(updates.size() == 1){
                        ObjectAnimator oa = ObjectAnimator.ofFloat(pager, "alpha", 1.0f);
                        ObjectAnimator oa2 = ObjectAnimator.ofFloat(splash, "alpha", 0.0f);
                        AnimatorSet as = new AnimatorSet();
                        as.setDuration(500l);
                        as.play(oa).with(oa2);
                        as.start();

                        //Also speak
                        HashMap<String, String> params = new HashMap<>();
                        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, String.valueOf(100));
                        tts.speak(updates.get(pager.getCurrentItem()).getPhrase(),
                                TextToSpeech.QUEUE_ADD, params);
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Error! Message: " + e.getMessage());
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

    @Override
    protected void onResume(){
        super.onResume();
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST);
        Log.d(TAG, "Prox data: " + sensor.getMaximumRange());
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
            Log.d(TAG, "Near at " + System.currentTimeMillis());
            downTime = System.currentTimeMillis();
        } else { //Far
            Log.d(TAG, "Far at " + System.currentTimeMillis());
            long diff = System.currentTimeMillis() - downTime;
            Log.d(TAG, "Difference: " + diff);

            if(downTime == -1) return; //We need a near first

            if(diff > 250l){ //Push
                tts.stop();
                moveDown();
            } else { //Swipe
                tts.stop();
                moveRight();
            }

            downTime = -1;
        }
    }

    private void moveRight() {
        Log.d(TAG, "Move right");
        movingDown = false;
        depth = 0;
        if (pager.getCurrentItem() < updates.size()-1) {
            //Play sound on the new page
            if (hasInit) {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, String.valueOf(200));
                Log.d(TAG, "Phrase: " + updates.get(pager.getCurrentItem() + 1).getPhrase());
                tts.speak(updates.get(pager.getCurrentItem() + 1).getPhrase(), TextToSpeech.QUEUE_ADD, params);
                Log.d(TAG, "Speaking");
            }

             pager.setCurrentItem(pager.getCurrentItem() + 1, true);
        }
    }

    private void moveDown(){
        //Cancel current utterance and scroll down if possible; otherwise reset and moveRight()
        if(!movingDown){
            movingDown = true;
            tts.stop();
        }
        depth++;
        if(depth < updates.get(pager.getCurrentItem()).getMoreInfo().size()){
            Log.d(TAG, "Current item: " + pager.getCurrentItem());
            Fragment fragment = upa.getItem(pager.getCurrentItem());
            if(fragment instanceof UpdateFragment){
                ((UpdateFragment)fragment).moveDown();
            } else {
                Log.d(TAG, "Not an update fragment");
            }

            //Also speak
            String utterance = updates.get(pager.getCurrentItem()).getMoreInfo().get(depth).first + " " +
                    updates.get(pager.getCurrentItem()).getMoreInfo().get(depth).second;
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, String.valueOf(100));
            tts.speak(utterance, TextToSpeech.QUEUE_ADD, params);
        } else {
            moveRight();
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
            Log.d(TAG, "Returning new update fragment...");
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
    }

    public class UpdateFragment extends Fragment {
        UpdateItem ui;
        View mainLayout;
        TextView title;
        TextView description;
        ImageView icon;
        View background;
        TextView slides;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_update, container, false);
        }

        @Override
        public void onActivityCreated(@Nullable Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            int position = getArguments().getInt("position");
            ui = updates.get(position);
            Log.d(TAG, "Update item at " + position + ": " + ui.getTitle());

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

            title.setText(ui.getTitle());
            description.setText(ui.getDescription());
            icon.setImageDrawable(ui.getIcon());
            if(ui.getBackgroundUrl() != null){
                Log.d(TAG, "Trying to get URL: " + ui.getBackgroundUrl());
                Picasso.with(this.getActivity())
                        .load(ui.getBackgroundUrl())
                        .resize(width, height)
                        .centerCrop()
                        .transform(new BlurTransformation(this.getActivity()))
                        .transform(new BrightnessFilterTransformation(this.getActivity(), 0.25f))
                        .into(backgroundImage, new Callback() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Successful loading image!");
                            }

                            @Override
                            public void onError() {
                                Log.d(TAG, "Failed loading image :(");
                            }
                        });
            }
            if(ui.getMoreInfo() != null && ui.getMoreInfo().size() > 0){
                slides.setText("More slides");
            } else {
                slides.setVisibility(View.GONE);
            }
        }

        public void moveDown(){
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
                    //Set text
                    title.setText(ui.getMoreInfo().get(depth).first);
                    description.setText(ui.getMoreInfo().get(depth).second);
                    int remainingSlides = ui.getMoreInfo().size() - 1 - depth;
                    if (remainingSlides > 0) {
                        slides.setText("More slides");
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

        public UpdateFragment(){
        }
    }
}
