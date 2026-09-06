package com.hack.kidhack.di

import com.hack.kidhack.background.ChildDeviceStatus
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.background.ParentPlatformController
import com.hack.kidhack.background.alert.ServerAlertBus
import com.hack.kidhack.background.alert.ServerAlertNotifier
import com.hack.kidhack.background.alert.ServerAlertSync
import com.hack.kidhack.background.alert.ParentChildWatcher
import com.hack.kidhack.background.command.ChildCommandBus
import com.hack.kidhack.background.command.ChildCommandNotifier
import com.hack.kidhack.background.push.PushMessageRouter
import com.hack.kidhack.background.push.PushTokenRegistrar
import com.hack.kidhack.background.location.GpsStatusMonitor
import com.hack.kidhack.background.usage.AppRuleWatcher
import com.hack.kidhack.background.usage.AppUsageCollector
import com.hack.kidhack.background.usage.ScreenTimeWatcher
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val backgroundModule = module {
    single { ChildCommandBus() }
    single { ChildDeviceStatus(androidContext()) }
    single { AppUsageCollector(androidContext(), get(), get()) }
    single { ScreenTimeWatcher(get(), androidContext(), get(), get()) }
    single { AppRuleWatcher(androidContext(), get(), get()) }
    single { ChildCommandNotifier(androidContext()) }
    single { PushTokenRegistrar(get(), get(), get()) }
    single { PushMessageRouter(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    single { GpsStatusMonitor(androidContext(), get(), get(), get()) }
    single { ChildPlatformController(androidContext(), get(), get(), get()) }
    single { ServerAlertBus() }
    single { ServerAlertNotifier(androidContext()) }
    single { ServerAlertSync(get(), get(), get(), get(), get()) }
    single { ParentChildWatcher(get(), get(), get(), get(), get(), get(), get()) }
    single { ParentPlatformController(androidContext(), get()) }
}
