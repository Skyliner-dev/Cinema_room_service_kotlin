package cinema

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.WebRequest
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class PurchaseRequest(
    val row: Int,
    val column: Int
)

class CinemaRoom(buildRows: Int, buildColumns: Int) {
    @JsonProperty("total_rows")
    var rows = buildRows

    @JsonProperty("total_columns")
    var columns = buildColumns

    @JsonProperty("available_seats")
    val availableSeats: List<Seat> = buildList {
        for (row in 1..rows) {
            for (column in 1..columns) add(Seat(row, column, price = if (row <= 4) 10 else 8))
        }
    }
}
class Re(seat:Seat){
    @JsonProperty("returned_ticket")
    val s:Seat = seat
}
data class Stat(
    var current_income:Int,
    var number_of_available_seats:Int,
    var number_of_purchased_tickets:Int
)
@SpringBootApplication
@ControllerAdvice
@RestController
class Controller {
    private val cinema = CinemaRoom(9, 9)
    private val cinemaSeatsBooked = mutableListOf<Seat>()
    private val cinemaSeatsAvailable = cinema.availableSeats.toMutableList()
    private val bookedToken = ConcurrentHashMap<UUID?,Seat>()
    private var totalIncome = 0
    @GetMapping("/seats")
    fun seats(): CinemaRoom {
        return cinema
    }
    data class TokenTicket(
        val token:UUID?,
        val ticket:Seat
    )
    data class TokenRequest(
        val token: UUID?
    )
    @PostMapping("/return")
    @ResponseBody
    fun returnTicket(@RequestBody token: TokenRequest): ResponseEntity<*> {
        val seat = bookedToken[token.token]
            ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CustomErrorMessage("Wrong token!"))
        cinemaSeatsBooked.remove(seat)
        bookedToken.remove(token.token)
        cinemaSeatsAvailable.add(seat)
        totalIncome -= seat.price
        return ResponseEntity.status(HttpStatus.OK).body(Re(seat))
    }
    @GetMapping("/stats")
    fun getStats(@RequestParam password: String?): ResponseEntity<Any> {
        return if (password == null) ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(CustomErrorMessage("The password is wrong!"))
        else {
            val stat = Stat(
                totalIncome, cinemaSeatsAvailable.size,
                cinemaSeatsBooked.size
            )
            ResponseEntity.status(HttpStatus.OK).body(stat)
        }
    }
    @PostMapping("/purchase")
    fun purchaseSeats(@RequestBody request: PurchaseRequest): TokenTicket {
        val foundSeat = cinema.availableSeats.find { it.row == request.row && it.column == request.column }

        if (foundSeat != null) {
            if (foundSeat !in cinemaSeatsAvailable) {
                throw TicketOccupiedException("The ticket has been already purchased!")
            }
            cinemaSeatsBooked.add(foundSeat)
            cinemaSeatsAvailable.remove(foundSeat)
            val token = UUID.randomUUID()
            totalIncome += foundSeat.price
            bookedToken[token] = foundSeat
            return TokenTicket(token,foundSeat)
        } else if (Seat(request.row, request.column, price = if (request.row <= 4) 10 else 8) !in cinema.availableSeats) {
            throw TicketOccupiedException("The number of a row or a column is out of bounds!")
        }

        throw TicketOccupiedException("The number of a row or a column is out of bounds!")
    }

    data class CustomErrorMessage(
        val error: String?
    )

    class TicketOccupiedException(message: String) : RuntimeException(message)

    @ExceptionHandler(TicketOccupiedException::class)
    fun seatNOTFound(
        e: TicketOccupiedException, request: WebRequest
    ): ResponseEntity<Any> {
        val body = CustomErrorMessage(
            e.message
        )
        return ResponseEntity(body, HttpStatus.BAD_REQUEST)
    }
}
fun main(args: Array<String>) {
    runApplication<Controller>(*args)
}