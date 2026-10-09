package com.aaaaaisss.necokey

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "NecokeY\nオフライン日本語IME"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "\n設定からキーボードを有効化すると、日本語入力に使えます。"
            textSize = 16f
        })
        root.addView(Button(this).apply {
            text = "キーボード設定を開く"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        })
        setContentView(root)
    }
}
