package com.example.mobileapplab1.ui.dictionary

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioAttributes.CONTENT_TYPE_MUSIC
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.example.mobileapplab1.*
import com.example.mobileapplab1.databinding.FragmentDictionaryBinding
import com.squareup.moshi.Moshi
import retrofit2.converter.moshi.MoshiConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

class DictionaryFragment : Fragment() {

    private var _binding: FragmentDictionaryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    lateinit var mediaPlayer: MediaPlayer

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentDictionaryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val moshi = Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.dictionaryapi.dev/api/v2/entries/en/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val service = retrofit.create(DictionaryApi::class.java)
        val meaningItems: MutableList<DictionaryMeaningItem> = mutableListOf()
        val dictionaryMeaningItemsAdapter = DictionaryMeaningItemsAdapter(meaningItems)

        mediaPlayer = MediaPlayer()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        mediaPlayer.setAudioAttributes(attributes)
        var playerAvailable = false

        val db = Room.databaseBuilder(
            binding.root.context,
            DictionaryDatabase::class.java, "dictionary-db"
        )
            .fallbackToDestructiveMigration()
            .build()

        var result: WordInformation? = null

        binding.textInputLayoutCookingSearch.setEndIconOnClickListener {
            val word: String = binding.dictionaryInputCooking.text.toString()
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    result = service.getWordMeaning(word)[0]
                } catch (e: Exception) {
                    val wordandMeanings = db.dictionaryDao().findByWord(word)
                    if (wordandMeanings == null) {
                        withContext(Dispatchers.Main) {
                            AlertDialog.Builder(binding.root.context)
                                .setTitle(getString(R.string.dictionary_not_found_title))
                                .setMessage(R.string.dictionary_not_found_desc)
                                .show()
                        }
                        return@launch
                    }
                    val definitionORMItems = mutableListOf<Definition>()
                    for (meaning in wordandMeanings.meanings) {
                        definitionORMItems += Definition(meaning.meaning, meaning.example)
                    }
                    result = WordInformation(
                        wordandMeanings.word.name,
                        listOf(Phonetics(wordandMeanings.word.transcription,"")),
                        listOf(Meaning(wordandMeanings.word.partOfSpeech!!, definitionORMItems)))
                }
                withContext(Dispatchers.Main) {
                    playerAvailable = false
                    if (result!!.phonetics[0].audio != "") {
                        mediaPlayer.setDataSource(result!!.phonetics[0].audio)
                        mediaPlayer.prepareAsync()
                        playerAvailable = true
                    }
                    binding.textWord.text = result!!.word
                    binding.textPhonetic.text = result!!.phonetics[0].transcription
                    binding.textPartOfSpeech.text = result!!.meanings[0].partOfSpeech
                    meaningItems.clear()
                    for (definition in result!!.meanings[0].definitions) {
                        meaningItems += DictionaryMeaningItem(definition.definition, definition.example)
                    }
                    dictionaryMeaningItemsAdapter.notifyDataSetChanged()

                }
            }
        }

        binding.imagePlaySound.setOnClickListener {
            if (playerAvailable) {
                mediaPlayer.start()
            }
        }

        binding.recyclerViewDictionary.layoutManager = LinearLayoutManager(root.context)
        binding.recyclerViewDictionary.adapter = dictionaryMeaningItemsAdapter



        binding.button.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                if (result != null) {
                    val nnResult = result!!
                    val word = WordEntity(
                        nnResult.word,
                        nnResult.phonetics[0].transcription,
                        nnResult.meanings[0].partOfSpeech,
                        nnResult.phonetics[0].audio
                    )
                    db.dictionaryDao().insertWord(word)
                    for (definition in result!!.meanings[0].definitions) {
                        db.dictionaryDao().insertMeaning(
                            MeaningEntity(
                                nnResult.word,
                                definition.definition,
                                definition.example
                            )
                        )
                    }
                }
            }
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}