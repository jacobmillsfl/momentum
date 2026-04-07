package com.momentum.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.momentum.app.data.repo.MomentumRepository

val LocalRepository = staticCompositionLocalOf<MomentumRepository> {
    error("MomentumRepository not provided")
}
