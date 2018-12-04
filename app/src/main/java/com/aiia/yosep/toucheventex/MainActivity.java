package com.aiia.yosep.toucheventex;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.os.Vibrator;

import java.util.Locale;

import static android.media.AudioRecord.ERROR;

/*
Author: Yosep Lee
HCI 과제로 제작중.
추후 Vibrator는 Thread로 제어되어야 할 필요가 있을 수 있음.
 */

public class MainActivity extends AppCompatActivity {


    //터치 사이의 인터벌을 재기 위한 변수
    long start = 0;
    long end = 0;

    //진동 관련 변수 제어
    final int MAX_VIBTIME = 3000;
    final int thresold = 150;
    final long wait = 150;

    //진동을 저장하기 위한 버퍼 변수와 포인터 플래그
    long[] buf;
    int[] normBuf;
    int normBufPos = 0;
    long[] initBuf = {150, 100, 150, 100, 2000};
    int bufPos = 0;

    //진동, tts 및 UI 객체
    Vibrator vibrator;
    private TextToSpeech tts;
    TextView textView1;
    TextView textView2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        textView1 = (TextView) findViewById(R.id.textView1);
        textView2 = (TextView) findViewById(R.id.TextView2);

        buf = new long[100];
        normBuf = new int[5];
        Button b1 = (Button)findViewById(R.id.button1);

        // TTS를 생성하고 OnInitListener로 초기화 한다.
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != ERROR) {
                    // 언어를 선택한다.
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });

        //버튼에 대한 기능 구현
        b1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                vibrator.vibrate(buf, -1); // miliSecond, 지정한 시간동안 진동
                printBuf(); //log
                clearBuf();
            }

        });
        vibrator.vibrate(initBuf, 0);
    }

    /*
    디스플레이에 터치 이벤트가 발생했을 때 처리 구현.
    터치 액션을 받아와서, 눌렀을 때, 뗐을 때를 구분하여 처리한다.
    눌렀을 때: MotionEvent.ACTION_DOWN
    뗐을 때: MotionEvent.ACTION_UP
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {

        long interval = 0;

        //get touch interval
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN :
                start = System.currentTimeMillis();
                vibrator.vibrate(MAX_VIBTIME);
                break;
            case MotionEvent.ACTION_UP :
                vibrator.cancel();
                end = System.currentTimeMillis();
                interval = end - start;
                textView2.setText("Touch Interval:  " + interval + " (" + end + " - " + start + ")");
                break;
            default :
                break;
        }

        //save interval

        //condition... threshold
        if(interval > thresold) {
            saveInterval(interval);

            int normedInterval = normalizeVibe(interval);
            normBuf[normBufPos++] = normedInterval;

            if(normBufPos > 2) {
                //TTS
                tts.setPitch(1f);         // 음성 톤 설정
                tts.setSpeechRate(1f);    // 읽는 속도 설정
                tts.speak("안녕하세요",TextToSpeech.QUEUE_FLUSH, null);  //tts로 스트링 읽기

                //display
                displayTextView();
                clearNormBuf();

                //vib
                vibrator.vibrate(initBuf, 0);
                return false;
            }
        }
        return false;
    }

    public void displayTextView() {
        //from normalized one.
        String toDisplay = "";
        for(int i = 0; i < normBufPos; i++) {
            switch (normBuf[i]) {
                case 0:
                    break;
                case 1:
                    toDisplay += "short ";
                    break;
                case 2:
                    toDisplay += "long ";
                    break;
            }
        }
        textView1.setText(toDisplay);
    }

    public void saveInterval(long interval) {
        Log.d("***Interval", interval+"");
        buf[bufPos++] = wait;
        buf[bufPos++] = interval;
        return;
    }

    public void printBuf() {
        for(int i = 0; i < bufPos; i++) {
            Log.d("***buf", buf[i] + "");
        }
    }

    public long getAllBufTime() {
        //get all time in millis of buf
        long sum = 0;
        for(int i = 0; i < bufPos; i++) {
            sum += buf[i];
        }
        return sum;
    }

    public void clearNormBuf() {
        for(int i = 0; i < normBufPos; i++) {
            normBuf[i] = 0;
        }
        normBufPos = 0;
    }

    public void clearBuf() {
        for(int i = 0; i < bufPos; i++) {
            buf[i] = 0;
        }
        bufPos = 0;
    }

    public int normalizeVibe(long interval) {
        // classity interver into short, long
        // short -> 1, long > 2.
        if(interval < 500) {
            //short
            return 1;
        }
        else if(interval > 499) {
            //long
            return 2;
        }
        return -1;
    }
}
