package dev.olaf.paperzero

import android.app.Application
import dev.olaf.paperzero.core.data.Pack
import dev.olaf.paperzero.core.data.PaperDatabase
import dev.olaf.paperzero.core.data.Progresses

class PaperZeroApplication : Application() {
    val pack by lazy { Pack(assets) }
    val progresses by lazy { Progresses(pack, PaperDatabase.create(this).dao()) }
}
