package team.bjtuss.bjtuselfservice.component

import androidx.compose.runtime.Composable

interface State


@Composable
fun ButtonStateContainer(canClick: Boolean, button: @Composable () -> Unit) {

}
