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
HCI 과제로 제작중. - 싱글터치
추후 Vibrator는 Thread로 제어되어야 할 필요가 있을 수 있음.
매핑 테이블과 입력된 코드와 일치 완료.

2018.12.04.
ISSUE#1: 입력하다 중간에 멈추게 되면? 타이머로 재고 있다가 refresh를 시켜주는 등 적용이 필요함.
ISSUE#2: 중간에 입력 취소 하고 싶으면?

2018.12.15. 주석 달기 완료.

추후 의사소통을 위한 인터페이스로 발전할 수 있지 않을까?
현재는 접촉수화만을 이용하고 있는데,
입력 방법론 등을 연구하여서 일반인과 의사소통 할 수 있는 인터페이스로 발전 가능하다.
관건은 이중감각장애자가 직관적으로 입력할 수 있는 약속들을 개발하는 것.
 */

public class MainActivity extends AppCompatActivity {


    //터치 사이의 인터벌을 재기 위한 변수
    long start = 0;
    long end = 0;

    //진동 관련 변수 제어
    final int MAX_VIBTIME = 3000;
    final int thresold = 130; //short 미만을 걸러내기 위한 수치.
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


    //안드로이드의 한 액티비티(화면)이 최초 실행될 때 실행되는 함수이다. 각종 값을 초기화 한다.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //진동을 수행하는 객체 초기화
        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

        //UI관련 객체 초기화
        setContentView(R.layout.activity_main);
        textView1 = (TextView) findViewById(R.id.textView1);
        textView2 = (TextView) findViewById(R.id.TextView2);
        Button b1 = (Button)findViewById(R.id.button1);

        //진동 패턴을 담을 배열을 초기화
        buf = new long[100];
        normBuf = new int[5];

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

        //대기 상태의 진동을 무제한 수행한다. repeat 0-> inf, 1-> once
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

        //접촉 전후 인터벌을 재기 위한 지역번수.
        long interval = 0;

        //get touch interval
        switch(event.getAction()) {
            case MotionEvent.ACTION_DOWN :
                //눌렀을 때 처리
                start = System.currentTimeMillis();
                vibrator.vibrate(MAX_VIBTIME);
                break;
            case MotionEvent.ACTION_UP :
                //뗐을 때 처리
                vibrator.cancel();
                end = System.currentTimeMillis();
                //인터벌 계산
                interval = end - start;
                textView2.setText("Touch Interval:  " + interval + " (" + end + " - " + start + ")");
                break;
            default :
                break;
        }

        //이벤트나 끝나고 난 후 후처리한다.
        //각기 다른 진동 시간을 normalize하여 일정하게 넣어주고
        //long인지 short인지 판별한다.

        //설정해 놓은 thresold 미만 시간의 진동은 무시한다.
        if(interval > thresold) {
            saveInterval(interval);
            int normedInterval = normalizeVibe(interval);
            normBuf[normBufPos++] = normedInterval;

            //입력 3개가 모두 full인 경우. 즉 3개의 입력을 모두 받은 경우 아래 처리한다.
            if(normBufPos > 2) {
                //112 등 코드를 사전에 예약된 스트링으로 변환시켜 오는 함수.
                String toSpeech = convertToText();

                //TTS
                tts.setPitch(1f);         // 음성 톤 설정
                tts.setSpeechRate(1f);    // 읽는 속도 설정
                tts.speak(toSpeech,TextToSpeech.QUEUE_FLUSH, null);  //tts로 스트링 읽기

                //UI에 보이도록 처리
                displayTextView();
                clearNormBuf();

                //모두 종료되면 다시 대기 상태의 패턴 진동이 일어나게 처리한다.
                vibrator.vibrate(initBuf, 0);
                return false;
            }
        }
        return false;
    }

    public String getCodeFromNormBuf() {
        String result = "";
        for(int i = 0; i < normBufPos; i++) {
            switch (normBuf[i]) {
                case 0:
                    break;
                case 1:
                    result += "1";
                    break;
                case 2:
                    result += "2";
                    break;
            }
        }
        return result;
    }

    /*
        입력된 신호와 그 의미를 매핑한다.
        매핑 테이블은 구현 아티팩트를 참고할 것.
        1과 2는 각각 short term vibration과 long term vibration을 의미한다.
     */
    public String convertToText() {
        /*
        normbuf에 들어있는 터치 값을 텍스트로 변환환다. 111,112, 등 ...
         */
        String code = getCodeFromNormBuf();
        Log.e("**code", code);
        String result;
        switch(code) {
            case "111":
                result = "이요셉 간병인을 불러주세요.";
                break;
            case "112":
                result = "전 시청각중복 장애인입니다.";
                break;
            case "121":
                result = "죄송합니다.";
                break;
            case "211":
                result = "가까운 병원으로 데려가 주세요.";
                break;
            case "122":
                result = "도와주세요!";
                break;
            case "212":
                result = "감사합니다.";
                break;
            case "221":
                result = "가까운 경찰서로 데려가 주세요.";
                break;
            case "222":
                result = "010 8891 5420 으로 연락 부탁드립니다.";
                break;
            default:
                result = "에러 에러";
                break;
        }
        return result;
    }

    //Long과 short를 판별하는 함수.
    public int normalizeVibe(long interval) {
        // classity interver into short, long
        // short -> 1, long > 2.
        if(interval < 350) {
            //short
            return 1;
        }
        else if(interval > 349) {
            //long
            return 2;
        }
        return -1;
    }

    public void displayTextView() {
        //from normalized one.
        String toDisplay = "";
        String toSpeak = convertToText();
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
        toDisplay = toDisplay + " \n " + toSpeak;
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

}
