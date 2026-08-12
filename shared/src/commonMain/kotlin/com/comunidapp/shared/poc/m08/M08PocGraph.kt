package com.comunidapp.shared.poc.m08

import com.comunidapp.shared.poc.m08.data.FakePetPocRepository
import com.comunidapp.shared.poc.m08.data.PetPocRepository

object M08PocGraph {
    fun repository(): PetPocRepository = FakePetPocRepository()
}
