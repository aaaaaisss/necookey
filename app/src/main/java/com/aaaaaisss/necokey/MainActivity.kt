package com.aaaaaisss.necokey

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.content.Intent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private val pickDictionary = 1001
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        root.addView(TextView(this).apply { text = "NecokeY\nオフライン日本語IME"; textSize = 24f })
        root.addView(TextView(this).apply {
            text = "\nまずは日本語入力の基本機能を実装しています。\n設定からキーボードを有効化できます。"
            textSize = 16f
        })
        root.addView(Button(this).apply {
            text = "キーボード設定を開く"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        root.addView(Button(this).apply {
            text = "Mozc UT辞書を更新"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/zip"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }, pickDictionary)
            }
        })
        setContentView(root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickDictionary || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val result = NecokeyUtDictionaryManager(this).importZip(uri)
        val message = result.fold(
            onSuccess = { it.message + "\n次回のIME起動から反映します。" },
            onFailure = { "辞書更新に失敗しました: ${it.message}" },
        )
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
