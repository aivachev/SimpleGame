package com.example.andrew.simplegame.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.andrew.simplegame.R;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Created by Andrew on 03.02.2018.
 */

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ReflexView extends View {
    //static instance variables
    private static final String HIGH_SCORE = "HIGH_SCORE";
    private SharedPreferences preferences;

    //Variables that manage the game
    private int spotsTouched;
    private int score;
    private int level;
    private int viewWidth;
    private int viewHeight;
    private long animationTime;
    private boolean gameOver;
    private boolean gamePaused;
    private boolean dialogDisplayed;
    private int highScore;

    //Collections types for our circles/spots (imageviews) and Animators
    private final Queue<ImageView> spots = new ConcurrentLinkedDeque<>();
    private final Queue<Animator> animators = new ConcurrentLinkedDeque<>();

    private TextView highScoreTextView;
    private TextView currentScoreTextView;
    private TextView levelTextView;
    private LinearLayout livesLinearLayout;
    private RelativeLayout relativeLayout;
    private Resources resources;
    private LayoutInflater layoutInflater;

    public static final int INITIAL_ANIMATION_DURATION = 6000; //6 seconds
    public static final Random random = new Random();
    public static final int SPOT_DIAMETER = 350;
    public static final float SCALE_X = 0.25f;
    public static final float SCALE_Y = 0.25f;
    public static final int INITIAL_SPOTS = 5;
    public static final int SPOT_DELAY = 500;
    public static final int LIVES = 3;
    public static final int MAX_LIVES = 7;
    public static final int NEW_LEVEL = 10;
    private Handler spotHandler;

    public static final int HIT_SOUND_ID = 1;
    public static final int MISS_SOUND_ID = 2;
    public static final int DISAPPEAR_SOUND_ID = 3;
    public static final int SOUND_PRIORITY = 1;
    public static final int SOUND_QUALITY = 100;
    public static final int MAX_STREAMS = 4;

    private SoundPool soundPool;
    private int volume;
    private Map<Integer, Integer> soundMap;


    public ReflexView(Context context, SharedPreferences sharedPreferences, RelativeLayout parentLayout) {
        super(context);

        preferences = sharedPreferences;
        highScore = preferences.getInt(HIGH_SCORE, 0);
        //save resources for loading external values

        //save LayoutInflater
        layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        //Setup UI components
        relativeLayout = parentLayout;
        livesLinearLayout = relativeLayout.findViewById(R.id.lifeLinearLayout);
        highScoreTextView = relativeLayout.findViewById(R.id.highScoreTextView);
        currentScoreTextView = relativeLayout.findViewById(R.id.scoreTextView);
        levelTextView = relativeLayout.findViewById(R.id.levelTextView);

        spotHandler = new Handler();
        //addNewSpot();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        viewWidth = w;
        viewHeight = h;
    }

    public void pause() {
        gamePaused = true;
        soundPool.release();
        soundPool = null;
        cancelAnimations();
    }

    public void resume(Context context) {
        gamePaused = false;
        initializeSoundEffects(context);
    }

    public void resetGame() {
        spots.clear();
        animators.clear();
        livesLinearLayout.removeAllViews();

        animationTime = INITIAL_ANIMATION_DURATION;

        spotsTouched = 0;
        score = 0;
        level = 1;
        gameOver = false;
        displayScores();

        //add lives
        for (int i = 0; i < LIVES; i++) {
            //add live indicator to screen
            livesLinearLayout.addView(
                    (ImageView) layoutInflater.inflate(R.layout.life, null)
            );
        }
        for (int i = 1; i <= INITIAL_SPOTS; i++) {
            spotHandler.postDelayed(addSpotRunnable, i + SPOT_DELAY);
        }
    }

    private void initializeSoundEffects(Context context) {
//        AudioAttributes aa = new AudioAttributes.Builder()
//                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                .setUsage(AudioAttributes.USAGE_GAME)
//                .build();

        soundPool = new SoundPool(MAX_STREAMS, AudioManager.STREAM_MUSIC, SOUND_QUALITY);

        //set sound effect volume
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC);

        //Create a sound map
        soundMap = new HashMap<Integer, Integer>();
        soundMap.put(HIT_SOUND_ID, soundPool.load(context,R.raw.hit, SOUND_PRIORITY));
        soundMap.put(MISS_SOUND_ID, soundPool.load(context, R.raw.miss, SOUND_PRIORITY));
        soundMap.put(DISAPPEAR_SOUND_ID, soundPool.load(context, R.raw.disappear, SOUND_PRIORITY));
    }
    private void displayScores() {
        highScoreTextView.setText(resources.getString(R.string.highScore) + " " + highScore);
        currentScoreTextView.setText(resources.getString(R.string.score) + " " + score);
        levelTextView.setText(resources.getString(R.string.level) + " " + level);

    }
    private Runnable addSpotRunnable = new Runnable() {
        @Override
        public void run() {
            addNewSpot();
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (soundPool != null) {
            soundPool.play(HIT_SOUND_ID, volume, volume, SOUND_PRIORITY, 0, 1.0f);
        }

        score -= 15 * level;
        score = Math.max(score, 0); //do not let score go below zero
        displayScores();

        return true;
    }

    public void addNewSpot() {

        int x = random.nextInt(viewWidth - SPOT_DIAMETER);
        int y = random.nextInt(viewHeight - SPOT_DIAMETER);
        int x2 = random.nextInt(viewWidth - SPOT_DIAMETER);
        int y2 = random.nextInt(viewHeight - SPOT_DIAMETER);

        //Create the actual spot/circle
        final ImageView spot = (ImageView) layoutInflater.inflate(R.layout.untouched, null);

        spots.add(spot);
        spot.setLayoutParams(new RelativeLayout.LayoutParams(SPOT_DIAMETER, SPOT_DIAMETER));


//        DisplayMetrics metrics = new DisplayMetrics();
//        WindowManager windowManager = (WindowManager) spot.getContext().getSystemService(Context.WINDOW_SERVICE);
//
//        windowManager.getDefaultDisplay().getMetrics(metrics);
//        int width = metrics.widthPixels;
//        int height = metrics.widthPixels;


        spot.setImageResource(random.nextInt(2) == 0 ? R.drawable.black_circle : R.drawable.blue_circle);

        spot.setX(x);
        spot.setY(y);


        spot.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                touchedSpot(spot);
                //Log.d("CLIQ", "Clicked! ");
            }
        });


        relativeLayout.addView(spot); //adding circle to the screen

        //add spot animations
        spot.animate().x(x2).y(y2).scaleX(SCALE_X).scaleY(SCALE_Y)
                .setDuration(animationTime).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                animators.add(animation); // save for later time
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                animators.remove(animation); //we are done, so remove it
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                if (!gamePaused && spots.contains(spot)) {
                    missedSpot(spot);
                }
            }
        });
    }

    private void missedSpot(ImageView spot) {
        spots.remove(spot);
        relativeLayout.removeView(spot);

        if (gameOver) {
            return;
        }

        if (soundPool != null) {
            soundPool.play(DISAPPEAR_SOUND_ID, volume, volume, SOUND_PRIORITY, 0, 1f);
        }

        if (livesLinearLayout.getChildCount() == 0){ //we have lost
            gameOver = true;

            if (score > highScore) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt(HIGH_SCORE,score);
                editor.apply();

                highScore = score;
            }
            cancelAnimations();

            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("GAME OVER");
            builder.setMessage("Score: " + score);
            builder.setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    displayScores();
                    dialogDisplayed = false;
                    resetGame();
                }
            });
            dialogDisplayed = true;
            builder.show();
        }else {
            livesLinearLayout.removeViewAt(
                    livesLinearLayout.getChildCount() - 1
            );
            addNewSpot();
        }

    }

    private void cancelAnimations() {
        for (Animator animator : animators) {
            animator.cancel();
        }

        //remove remaining spots from the screen
        for (ImageView view : spots) {
            relativeLayout.removeView(view);
        }

        spotHandler.removeCallbacks(addSpotRunnable);
        animators.clear();
        spots.clear();
    }


    private void touchedSpot(ImageView spot) {
        relativeLayout.removeView(spot);
        spots.remove(spot);

        //level = 1;

        ++spotsTouched;
        score += 10 * level;

        if (spotsTouched % NEW_LEVEL == 0) {
            ++level;
            animationTime *= 0.95; // make game 5% faster

            if (livesLinearLayout.getChildCount() < MAX_LIVES) {
                ImageView life = (ImageView) layoutInflater.inflate(R.layout.life, null);
                livesLinearLayout.addView(life);
            }
        }

        displayScores();

        if (!gameOver) {
            addNewSpot();
        }
        //currentScoreTextView.setText("Score: " + score);
    }
}
