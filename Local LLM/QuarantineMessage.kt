private suspend fun moveToQuarantine(
    sms:String,
    aiResult:String
){

    val confidence =
        Regex("\\d+")
            .find(aiResult)
            ?.value
            ?.toInt()
            ?:0


    val malicious =
        aiResult.contains(
            "MALICIOUS",
            true
        )


    if(
        malicious &&
        confidence >= 70
    ){


        val database =
            Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "phishguard.db"
            )
            .build()



        val dao =
            database.quarantineDAO()



        dao.insert(

            QuarantineMessage(

                message = sms,

                classification =
                    "MALICIOUS",

                confidence =
                    confidence,

                reason =
                    aiResult

            )

        )

    }

}
