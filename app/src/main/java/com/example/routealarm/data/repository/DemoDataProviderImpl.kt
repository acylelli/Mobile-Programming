package com.example.routealarm.data.repository

import android.content.Context
import com.example.routealarm.R
import com.example.routealarm.data.remote.fake.FakeTransitRemoteDataSource
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.repository.DemoDataProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DemoDataProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DemoDataProvider {
    override fun demoOrigin() = Place(
        name = context.getString(R.string.demo_origin_name),
        address = context.getString(R.string.demo_origin_address),
        latitude = FakeTransitRemoteDataSource.DEMO_ORIGIN.latitude,
        longitude = FakeTransitRemoteDataSource.DEMO_ORIGIN.longitude,
        type = PlaceType.HOME,
        isDemo = true,
    )

    override fun demoDestination() = Place(
        name = context.getString(R.string.demo_destination_name),
        address = context.getString(R.string.demo_destination_address),
        latitude = FakeTransitRemoteDataSource.DEMO_DESTINATION.latitude,
        longitude = FakeTransitRemoteDataSource.DEMO_DESTINATION.longitude,
        type = PlaceType.SCHOOL,
        isDemo = true,
    )

    override fun demoTitle(): String = context.getString(R.string.demo_schedule_title)
}
