package com.github.kr328.clash.service.clash.module

import android.app.Service
import com.github.kr328.clash.service.TrafficHistory
import kotlinx.coroutines.delay

class TrafficHistoryModule(service: Service) : Module<Unit>(service) {
    override suspend fun run() {
        TrafficHistory.start(service)
        try {
            while (true) {
                delay(30_000)
                TrafficHistory.sample(service)
            }
        } finally {
            TrafficHistory.stop(service)
        }
    }
}
