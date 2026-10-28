package pl.edu.slowscore.data

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.FirebaseApp

fun setupAppCheck(app: FirebaseApp) {
    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
