package com.timo.timoterminal.fragmentViews

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.timo.timoterminal.databinding.FragmentAttendanceBinding
import com.timo.timoterminal.service.HttpService
import com.timo.timoterminal.viewModel.AttendanceFragmentViewModel
import com.zkteco.android.core.interfaces.RfidListener
import com.zkteco.android.core.sdk.service.RfidService
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Date

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"


class AttendanceFragment : Fragment(), RfidListener {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var binding: FragmentAttendanceBinding
    private val httpService: HttpService = HttpService()
    private val viewModel: AttendanceFragmentViewModel by viewModel()
    private var funcCode = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAttendanceBinding.inflate(inflater, container, false)

        setOnClickListeners()

        return binding.root
    }

    // remove listener on pause
    override fun onPause() {
        RfidService.unregister()
        binding.textViewAttendanceState.text = "Keine Anwesenheit ausgewählt"

        super.onPause()
    }

    // set booking code and start listening
    private fun setOnClickListeners() {
        binding.buttonTestKommen.setOnClickListener {
            funcCode = 100
            setListener()
            binding.textViewAttendanceState.text = "Kommen"
        }
        binding.buttonTestGehen.setOnClickListener {
            funcCode = 200
            setListener()
            binding.textViewAttendanceState.text = "Gehen"
        }
        binding.buttonTestPauseAnfang.setOnClickListener {
            funcCode = 110
            setListener()
            binding.textViewAttendanceState.text = "Pause Dynamisch"
        }
        binding.buttonTestPauseEnde.setOnClickListener {
            funcCode = 210
            setListener()
            binding.textViewAttendanceState.text = "Pause nur Ende"
        }
    }

    // start listening to card reader
    private fun setListener(){
        RfidService.setListener(this)
        RfidService.register()
    }

    // send all necessary information to timo to create a booking
    private fun sendBooking(card :String ,inputCode : Int){
        val dateFormatter  = SimpleDateFormat("dd.MM.yyyy HH:mm:ss")
        viewModel.viewModelScope.launch {
            val url = viewModel.getURl()
            val company = viewModel.getCompany()
            httpService.post(
                "${url}services/rest/zktecoTerminal/booking",
                mapOf(
                    Pair("card", card),
                    Pair("firma", company),
                    Pair("date", dateFormatter.format(Date())),
                    Pair("funcCode", "$funcCode"),
                    Pair("inputCode", "$inputCode"),
                    Pair("terminalId", "DU")
                ),
                { _, _, msg ->
                    if (msg != null) {
                        Snackbar.make(binding.root,msg,Snackbar.LENGTH_LONG).show()
                    }
                },
                {}
            )
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment AttendanceFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            AttendanceFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    // get code of scanned card
    override fun onRfidRead(rfidInfo: String) {
        val rfidCode = rfidInfo.toLongOrNull(16)
        if (rfidCode != null) {
            var oct = rfidCode.toString(8)
            while (oct.length < 9) {
                oct = "0$oct"
            }
            oct = oct.reversed()
            if(funcCode > 0) {
                sendBooking(oct,2)
                funcCode = -1
                RfidService.unregister()
                binding.textViewAttendanceState.text = "Keine Anwesenheit ausgewählt"
            }
        }
    }
}