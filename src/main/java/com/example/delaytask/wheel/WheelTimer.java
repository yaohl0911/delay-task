package com.example.delaytask.wheel;

import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
@AllArgsConstructor
public class WheelTimer implements TimerTask {

        boolean flag;

        @Override
        public void run(Timeout timeout) throws Exception {
            log.info("start to delete the order");
            this.flag =false;
        }

    public static void main(String[] argv) {

        WheelTimer timerTask = new WheelTimer(true);

        Timer timer = new HashedWheelTimer();

        timer.newTimeout(timerTask, 5, TimeUnit.SECONDS);

        int i = 1;

        while(timerTask.flag){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("{} seconds passed", i);
            i++;
        }
    }
}
