package com.example.kronos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
            .setupWithNavController(navHost.navController)
    }
}