package pl.edu.slowscore.data

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.FirebaseApp

fun setupAppCheck(app: FirebaseApp) {
    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
