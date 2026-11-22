// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ambivalenton.openv.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.ambivalenton.openv.MainActivity

/** A launcher alias never owns the visible app task. Disabling it cannot finish MainActivity. */
class LauncherEntryActivity:Activity(){
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        startActivity(Intent(this,MainActivity::class.java).apply{
            action=intent.action;data=intent.data
            intent.extras?.let{putExtras(it)}
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
    }
}
