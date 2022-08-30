/*
 * Copyright (c) 2021, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.psiphon3.psicash.account;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.psiphon3.TunnelState;
import com.psiphon3.psicash.mvibase.MviIntent;

import io.reactivex.Flowable;

public interface PsiCashAccountIntent extends MviIntent {
    @AutoValue
    abstract class InitialIntent implements PsiCashAccountIntent {
        public static InitialIntent create() {
            return new AutoValue_PsiCashAccountIntent_InitialIntent();
        }
    }

    @AutoValue
    abstract class GetPsiCash implements PsiCashAccountIntent {
        public static GetPsiCash create(Flowable<TunnelState> tunnelStateFlowable) {
            return new AutoValue_PsiCashAccountIntent_GetPsiCash(tunnelStateFlowable);
        }
        abstract Flowable<TunnelState> tunnelStateFlowable();
    }

    @AutoValue
    abstract class LoginAccount implements PsiCashAccountIntent {
        public static LoginAccount create(Flowable<TunnelState> tunnelStateFlowable,
                @Nullable String username,
                                          @Nullable String password) {
            return new AutoValue_PsiCashAccountIntent_LoginAccount(tunnelStateFlowable, username, password);
        }

        abstract Flowable<TunnelState> tunnelStateFlowable();

        @Nullable
        public abstract String username();

        @Nullable
        public abstract String password();
    }
}