package com.example.myapplication

import android.app.Dialog
import android.content.Context
import android.icu.util.Calendar
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import org.w3c.dom.Text
import java.util.Date

class RecruitAdapter(private var recruitList: List<RecruitDataModel>, private val context: Context) :
    RecyclerView.Adapter<RecruitAdapter.RecruitViewHolder>() {

    inner class RecruitViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.recruitTitle)
        val baptime: TextView = itemView.findViewById(R.id.recruitBaptime)
        val place: TextView = itemView.findViewById(R.id.recruitPlace)
        val uploader: TextView = itemView.findViewById(R.id.recruitUploader)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecruitViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recruit, parent, false)
        return RecruitViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: RecruitViewHolder, position: Int) {
        val recruit = recruitList[position]
        val firestore = FirebaseFirestore.getInstance()
        // 리스트에 유저 이름 표시
        var uploaderName: String = ""
        var uploaderAge: Long = -1
        var uploaderMajor: String = ""
        var recruitContent: String = ""
        var keywordMBTI: String = ""
        var keywordAgeMin: Long = -1
        var keywordAgeMax: Long = -1
        var keywordSex: String = ""

        // db에서 데이터 긁어오기
        firestore.collection("user").document(recruit.uploader_id).get().addOnSuccessListener {document->
            keywordMBTI = document.getString("keyword_mbti")?: ""
            keywordSex = document.getString("keyword_sex")?: ""
            keywordAgeMax = document.getLong("keyword_age_max")?:-1
            keywordAgeMin = document.getLong("keyword_age_min")?:-1

            val birthday = document.getTimestamp("birthday")?.toDate()
            val date: Date? = birthday
            val calendar: Calendar = Calendar.getInstance()
            val currentYear = calendar.get(Calendar.YEAR)
            calendar.time = date
            val userBirthYear = calendar.get(Calendar.YEAR)

            uploaderAge = (currentYear - userBirthYear).toLong()
            uploaderMajor = document.getString("major")?:""

            uploaderName = document.getString("name")?:"알 수 없음"
            holder.uploader.text = uploaderName

        }.addOnFailureListener {
            holder.uploader.text = "탈퇴한 사용자"
        }

        // 제목, 인원, 식사장소 표시
        holder.title.text = "${recruit.title} (${recruit.headcount_current} / ${recruit.headcount_max})"
        holder.place.text = "식사 장소 : ${recruit.place}"
        // 모집 시간 표시
        val date = recruit.baptime?.toDate()
        val calendar: Calendar = Calendar.getInstance()
        calendar.time = date
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toString()
        val minute = calendar.get(Calendar.MINUTE).toString()
        holder.baptime.text = "식사 시간 : ${hour}시 ${minute}분"

        // 모집글 세부조회
        holder.itemView.setOnClickListener {
            val dialog = Dialog(holder.itemView.context)
            dialog.setContentView(R.layout.detail_recruit)

            val titleTextView = dialog.findViewById<TextView>(R.id.tv_Title)
            val placeTextView = dialog.findViewById<TextView>(R.id.tv_Place)
            val baptimeTextView = dialog.findViewById<TextView>(R.id.tv_Time)
            val tv_Age = dialog.findViewById<TextView>(R.id.tv_Age)
            val tv_Content = dialog.findViewById<TextView>(R.id.tv_Content)

            val tv_keywordMajor = dialog.findViewById<TextView>(R.id.tv_KeywordMajor)
            val tv_keywordSex = dialog.findViewById<TextView>(R.id.tv_KeywordSex)
            val tv_keywordMBTI = dialog.findViewById<TextView>(R.id.tv_KeywordMBTI)
            val tv_keywordAge = dialog.findViewById<TextView>(R.id.tv_KeywordAge)
            val tv_uploaderName = dialog.findViewById<TextView>(R.id.tv_Name)

            // 닫기 버튼
            val btClosePopup = dialog.findViewById<ImageButton>(R.id.ibt_ClosePopup)
            btClosePopup.setOnClickListener {
                Log.i("팝업버튼 닫기", "클릭확인됨")
                dialog.dismiss()
            }

            // 아이템 데이터 설정
            titleTextView.text = recruit.title
            placeTextView.text = "식사 장소 : ${recruit.place}"
            baptimeTextView.text = "식사 시간 : ${hour}시 ${minute}분"
            tv_uploaderName.text = "모집자 : " + uploaderName
            tv_Age.text = "모집자 나이 : ${uploaderAge}세"


            //mbti 태그
            if (recruit.keyword_mbti == "") {
                tv_keywordMBTI.text  = "#MBTI 비공개"
            } else {
                tv_keywordMBTI.text  = "#" + recruit.keyword_mbti
            }


            if (recruit.keyword_sex == "") {
                tv_keywordSex.text = "#성별제한 없음"
            } else {
                tv_keywordSex.text = "#" + recruit.keyword_sex + " 만"
            }

            // 확과 설정
            tv_keywordMajor.text = "#" + recruit.keyword_major

            if (keywordAgeMax.toInt() == -1) {
                if (keywordAgeMin.toInt() == -1) {
                    tv_keywordAge.text = "나이제한 없음"
                } else {
                    tv_keywordAge.text = "$keywordAgeMin ~ 세만"
                }
            }
            else {
                if (keywordAgeMin.toInt() == -1) {
                    tv_keywordAge.text = "~ ${keywordAgeMax}세만"
                } else {
                    tv_keywordAge.text = "$keywordAgeMin ~ ${keywordAgeMax}세만"
                }
            }

            tv_Content.text = recruit.content



            // 참여하기
            val btJoin = dialog.findViewById<Button>(R.id.bt_EnterRecruit)
            btJoin.setOnClickListener {
                Log.i("참여하기", "클릭됨")
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    return@setOnClickListener
                }

                val database: DatabaseReference = FirebaseDatabase.getInstance().reference
                val firestore = FirebaseFirestore.getInstance()
                // 최대 참여자 수에 도달한 경우 참여 거절. 아니라면 참여
                firestore.collection("recruitment").document(recruit.post_id).get().addOnSuccessListener { document ->
                    val currentHeadcount = document.getLong("headcount_current") ?: 0
                    val maxHeadcount = document.getLong("headcount_max") ?: 0
                    if (currentHeadcount >= maxHeadcount) {
                        popUpDialog(context, "이미 꽉 찬 모집입니다.")
                    } else {
                        // 채팅방의 사용자 목록에 현재 사용자 추가
                        database.child("chatRooms").child(recruit.post_id).child("users").child(currentUser.uid).setValue(true)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Log.i("채팅방 참여 성공", "ㅇㅇ")
                                    val firestore = FirebaseFirestore.getInstance()
                                    val chatRoomData = firestore.collection("recruitment").document(recruit.post_id)
                                    chatRoomData.get().addOnSuccessListener { document ->
                                        val headcountCurrent = document.getLong("headcount_current") ?: 0
                                        // 현재 인원 업데이트
                                        val update = hashMapOf<String, Any>(
                                            "headcount_current" to (headcountCurrent + 1),
                                        )
                                        firestore.collection("recruitment").document(recruit.post_id).update(update)
                                    }
                                } else {
                                    popUpDialog(context, "참여에 실패했습니다.")
                                }
                            }
                    }
                }
            }
            dialog.show()
        }
    }

    override fun getItemCount() = recruitList.size

    fun popUpDialog(context: Context, msg: String) {
        val dialogBuilder = AlertDialog.Builder(context)
        dialogBuilder.setTitle("")
        dialogBuilder.setMessage(msg)
        // 다이얼로그 팝업
        dialogBuilder.setNegativeButton("닫기") { dialog, _ ->
            dialog.dismiss()
        }
        dialogBuilder.create().show()
    }

    fun updateList(newList: List<RecruitDataModel>) {
        recruitList = newList
        notifyDataSetChanged()
    }
}