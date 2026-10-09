package com.aaaaaisss.necokey

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "necookey\nオフライン日本語IME"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "\nキーボードを有効にして、入力方法として選択してください。"
            textSize = 16f
            setTextColor(Color.WHITE)
        })
        root.addView(Button(this).apply {
            text = "キーボードを有効にする"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })
        setContentView(root)
    }
}
