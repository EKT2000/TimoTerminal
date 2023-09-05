package com.timo.timoterminal.viewModel

import android.app.Application
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timo.timoterminal.entityClasses.DemoEntity
import com.timo.timoterminal.entityClasses.UserEntity
import com.timo.timoterminal.repositories.DemoRepository
import com.timo.timoterminal.repositories.UserRepository
import com.timo.timoterminal.service.HttpService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainActivityViewModel(private val demoRepository: DemoRepository, private val userRepository: UserRepository, private val httpService: HttpService) : ViewModel() {
    val demoEntities : Flow<List<DemoEntity>> = demoRepository.getAllEntities
    val userEntities : Flow<List<UserEntity>> = userRepository.getAllEntities

    fun initHearbeatService(application: Application, lifecycleOwner: LifecycleOwner ) {
        httpService.initHearbeatWorker(application, lifecycleOwner)
    }

    fun killHeartBeatWorkers(application: Application){
        httpService.killHeartBeatWorkers(application)
    }

    // db changes should not run on mainScope, this is why we run it in viewModelScope independently
    fun addEntity(demoEntity: DemoEntity) {
        viewModelScope.launch {
            demoRepository.insertDemoEntity(demoEntity)
        }
    }

    fun addEntity(userEntity: UserEntity) {
        viewModelScope.launch {
            userRepository.insertUserEntity(userEntity)
        }
    }

    suspend fun count() = userRepository.count()
}

