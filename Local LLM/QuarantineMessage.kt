suspend fun updateMessageStatus(
    messageId: Int,
    riskScore: Int,
    database: AppDatabase
) {

    val status = when {

        riskScore >= 70 ->
            "quarantined"

        riskScore >= 35 ->
            "caution"

        else ->
            "safe"
    }


    database.messageDao()
        .updateStatus(
            id = messageId,
            status = status
        )
}

@Dao
interface MessageDao {


    @Query(
        """
        UPDATE analyzed_messages
        SET status = :status
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: Int,
        status: String
    )


}
