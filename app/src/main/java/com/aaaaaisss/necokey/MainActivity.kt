package com.aaaaaisss.necokey

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "necookey"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "オフライン日本語キーボード"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(4), 0, dp(20))
        })
        status = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(status)
        root.addView(button("カスタムキーボード・設定を開く") {
            startActivity(Intent().setClassName(
                this,
                "com.kazumaproject.markdownhelperkeyboard.setting_activity.MainActivity"
            ).putExtra("openSettingActivity", "setting_fragment_request"))
        })
        root.addView(button("キーボードを有効にする") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("使用するキーボードを選ぶ") {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        })
        root.addView(TextView(this).apply {
            text = "キーボードの動作テスト"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(24), 0, dp(8))
        })
        input = EditText(this).apply {
            hint = "ここをタップして日本語を入力"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            minLines = 2
            gravity = Gravity.TOP
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.rgb(35, 35, 35))
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(112)))
        root.addView(button("テスト欄に入力する") {
            input.requestFocus()
            input.post {
                getSystemService(InputMethodManager::class.java)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        })
        root.addView(TextView(this).apply {
            text = "ここでキーボードの表示と入力を確認できます。"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(12), 0, 0)
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val imm = getSystemService(InputMethodManager::class.java)
        val id = "$packageName/$packageName.NecokeYInputMethodService"
        val enabled = imm.enabledInputMethodList.any { it.id == id }
        val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == id
        status.text = when {
            selected -> "状態：necookey が選択されています"
            enabled -> "状態：有効です。入力方法として選択してください"
            else -> "状態：まだ有効化されていません"
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}