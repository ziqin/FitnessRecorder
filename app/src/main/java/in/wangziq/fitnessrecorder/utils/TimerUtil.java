package in.wangziq.fitnessrecorder.utils;

import java.util.Timer;
import java.util.TimerTask;

public final class TimerUtil {

    public static Timer doAfter(int delay, Runnable task) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, delay);
        return timer;
    }

    public static Timer repeatPer(int period, Runnable task) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                task.run();
            }
        }, period, period);
        return timer;
    }
}
