package com.example.noaa.homeactivity.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noaa.model.Coordinate
import com.example.noaa.model.Place
import com.example.noaa.model.RepoInterface
import com.example.noaa.model.WeatherResponse
import com.example.noaa.services.network.ApiState
import com.example.noaa.utilities.Constants
import com.example.noaa.utilities.PermissionUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SharedViewModel(
    private val repo: RepoInterface
) : ViewModel() {

    private val _locationStatusMutableStateFlow: MutableStateFlow<String> = MutableStateFlow("")
    val locationStatusStateFlow: StateFlow<String> get() = _locationStatusMutableStateFlow

    private val _coordinateMutableStateFlow: MutableStateFlow<Coordinate> =
        MutableStateFlow(Coordinate(0.0, 0.0))
    val coordinateStateFlow: StateFlow<Coordinate> get() = _coordinateMutableStateFlow

    private val _weatherResponseMutableStateFlow: MutableStateFlow<ApiState> =
        MutableStateFlow(ApiState.Loading)
    val weatherResponseStateFlow: StateFlow<ApiState> get() = _weatherResponseMutableStateFlow

    fun getLocation(context: Context) {
        if (PermissionUtility.checkConnection(context)) {
            when (readStringFromSettingSP(Constants.LOCATION)) {
                Constants.GPS -> {
                    if (PermissionUtility.checkPermission(context)) {
                        if (PermissionUtility.isLocationIsEnabled(context)) {
                            viewModelScope.launch {
                                repo.getCurrentLocation().collectLatest {
                                    _coordinateMutableStateFlow.value = it
                                }
                            }
                        } else {
                            _locationStatusMutableStateFlow.value = Constants.SHOW_DIALOG
                        }
                    } else {
                        _locationStatusMutableStateFlow.value = Constants.REQUEST_PERMISSION
                    }
                }

                Constants.MAP -> {
                    _locationStatusMutableStateFlow.value = Constants.TRANSITION_TO_MAP
                }

                else -> {
                    _locationStatusMutableStateFlow.value = Constants.SHOW_INITIAL_DIALOG
                }
            }
        } else {
            getCashedData()
        }
    }

    fun getWeatherData(coordinate: Coordinate, language: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.getWeatherResponse(coordinate, language).catch {
                    _weatherResponseMutableStateFlow.value = ApiState.Failure(it.message!!)
                }.collect { weatherResponse ->
                    if (weatherResponse.isSuccessful) {
                        _weatherResponseMutableStateFlow.value =
                            ApiState.Success(weatherResponse.body()!!)
                    } else {
                        _weatherResponseMutableStateFlow.value =
                            ApiState.Failure(weatherResponse.message())
                    }
                }
            }catch (_:Exception){
                repo.getCashedData()
            }
        }
    }


    fun insertPlaceToFav(place: Place) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertPlaceToFav(place)
        }
    }

    fun insertCashedData(weatherResponse: WeatherResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            repo.insertCashedData(weatherResponse)
        }
    }

    private fun getCashedData() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.getCashedData()?.collect {
                try {
                    _weatherResponseMutableStateFlow.value = ApiState.Success(it)
                } catch (_: Exception) {
                    _weatherResponseMutableStateFlow.value =
                        ApiState.Failure("No Internet Connection")
                }
            }
        }
    }

    fun checkConnection(context: Context): Boolean {
        return PermissionUtility.checkConnection(context)
    }

    fun writeStringToSettingSP(key: String, value: String) {
        repo.writeStringToSettingSP(key, value)
    }

    fun readStringFromSettingSP(key: String): String {
        return repo.readStringFromSettingSP(key)
    }

    fun writeFloatToSettingSP(key: String, value: Float) {
        repo.writeFloatToSettingSP(key, value)
    }

    fun readFloatFromSettingSP(key: String): Float {
        return repo.readFloatFromSettingSP(key)
    }

}