package com.crylo.splitbill

import android.app.Application
import com.crylo.splitbill.data.Repository
import com.crylo.splitbill.data.SplitDatabase

class SplitBillApp : Application() {
    val repository: Repository by lazy { Repository(SplitDatabase.create(this).dao()) }
}
