package de.androidcrypto.nfcemvccreaderdevnied;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.github.devnied.emvnfccard.enums.EmvCardScheme;
import com.github.devnied.emvnfccard.iso7816emv.EmvTags;
import com.github.devnied.emvnfccard.model.Application;
import com.github.devnied.emvnfccard.model.EmvCard;

import de.androidcrypto.nfcemvccreaderdevnied.model.EmvCardAnalyze;

import com.github.devnied.emvnfccard.model.enums.ServiceCode3Enum;
import com.github.devnied.emvnfccard.parser.EmvTemplate;
import com.github.devnied.emvnfccard.utils.TlvUtil;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.androidcrypto.nfcemvccreaderdevnied.utils.ApplicationInterchangeProfile;
import de.androidcrypto.nfcemvccreaderdevnied.utils.AtrUtils;
import de.androidcrypto.nfcemvccreaderdevnied.utils.CVMList;
import fr.devnied.bitlib.BytesUtils;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {

    TextView readResult;
    private NfcAdapter mNfcAdapter;
    String dumpExportString = "";
    String tagIdString = "";
    String tagTypeString = "";
    private static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 100;
    Context contextSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.main_toolbar);
        setSupportActionBar(myToolbar);
        contextSave = getApplicationContext();
        readResult = findViewById(R.id.tvMainReadResult);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    // This method is run in another thread when a card is discovered
    // !!!! This method cannot cannot direct interact with the UI Thread
    // Use `runOnUiThread` method to change the UI from this method
    @Override
    public void onTagDiscovered(Tag tag) {

        IsoDep isoDep = null;

        // Whole process is put into a big try-catch trying to catch the transceive's IOException
        try {
            isoDep = IsoDep.get(tag);
            // Make a Sound
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createOneShot(150, 10));
            } else {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                v.vibrate(200);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //UI related things, not important for NFC
                    readResult.setText("");
                }
            });
            isoDep.connect();
            byte[] response;
            String idContentString = "Content of ISO-DEP tag";

            PcscProvider provider = new PcscProvider();
            provider.setmTagCom(isoDep);

            EmvTemplate.Config config = EmvTemplate.Config()
                    .setContactLess(true)
                    .setReadAllAids(true)
                    .setReadTransactions(true)
                    .setRemoveDefaultParsers(false)
                    .setReadAt(true);

            EmvTemplate parser = EmvTemplate.Builder()
                    .setProvider(provider)
                    .setConfig(config)
                    .build();

            // todo check ResponseSuccess

            // single task starts
            idContentString = idContentString + "\n" + "---- single task start ----";
            idContentString = idContentString + "\n" + "---- step 01: selectPPSE ----";
            byte[] selectPpseResponse = parser.selectPpse();
            idContentString = idContentString + "\n" + "selectPpseResponse: " + BytesUtils.bytesToString(selectPpseResponse);
            idContentString = idContentString + "\n" + TlvUtil.prettyPrintAPDUResponse(selectPpseResponse);

            idContentString = idContentString + "\n" + "---- step 02: get AID(s) from response ----";
            List<byte[]> aidsList = parser.getAidsFromPpseResponse(selectPpseResponse);
            idContentString = idContentString + "\n" + "nr of aids found: " + aidsList.size();
            for (int i = 0; i < aidsList.size(); i++) {
                idContentString = idContentString + "\n" + "aid nr: " + i + " AID: " + BytesUtils.bytesToString(aidsList.get(i));
            }
            idContentString = idContentString + "\n" + "---- step 02: getAids ends ----";

            idContentString = idContentString + "\n" + "step 03 select PID with AID";

            // todo this should be used in emvCardAnalyze: apduSelectPidResponses
            List<byte[]> apduSelectPidResponses = new ArrayList<byte[]>();
            for (int i = 0; i < aidsList.size(); i++) {
                byte[] selectedAid = aidsList.get(i);
                idContentString = idContentString + "\n" + "aid nr: " + i + " is: " + BytesUtils.bytesToString(selectedAid);
                byte[] selectPidResponse = parser.selectPid(selectedAid);
                if (selectPidResponse != null) {
                    idContentString = idContentString + "\n" + TlvUtil.prettyPrintAPDUResponse(selectPidResponse);
                    apduSelectPidResponses.add(selectPidResponse);
                } else {
                    apduSelectPidResponses.add(new byte[0]);
                }
                // should return the PDOL
                idContentString = idContentString + "\n" + "selectPidResponse: " + BytesUtils.bytesToString(selectPidResponse);
                idContentString = idContentString + "\n" + "-------------------";
            }
            idContentString = idContentString + "\n" + "---- step 03: selectPid ends ----";

            idContentString = idContentString + "\n" + "---- step 04 get Processing Options (PDOL) ----";
            // todo this should be used in emvCardAnalyze: apduSelectPidResponses
            List<byte[]> gpos = new ArrayList<byte[]>();
            int apduSelectPidResponsesSize = apduSelectPidResponses.size();
            idContentString = idContentString + "\n" + "we do have " + apduSelectPidResponsesSize + " gpos to process";
            for (int i = 0; i < apduSelectPidResponsesSize; i++) {
                byte[] selectPidResponse = apduSelectPidResponses.get(i);
                // this works for all cards but DKB Visa Debit
                byte[] gpo = parser.parseSelectResponse(selectPidResponse);
                // this is a test for DKB Visa debit only
                //byte[] gpoVisa = parser.parseSelectResponseVisa(); // works !
                if (gpo == null) {
                    idContentString = idContentString + "\n" + "Notice: even if there is more than one AID only the first AID is run !";
                    gpo = parser.getGpoForVisaCards();
                }
                gpos.add(gpo);

                idContentString = idContentString + "\n" + "gpo nr: " + i;
                idContentString = idContentString + "\n" + "selectPidResponse: " + BytesUtils.bytesToString(selectPidResponse);
                idContentString = idContentString + "\n" + "gpo: " + BytesUtils.bytesToString(gpo);
                idContentString = idContentString + "\n" + TlvUtil.prettyPrintAPDUResponse(gpo);
                //idContentString = idContentString + "\n" + "gpoV:" + BytesUtils.bytesToString(gpoVisa);
                idContentString = idContentString + "\n" + "-------------------";
            }
            idContentString = idContentString + "\n" + "---- step 04 get Processing Options (PDOL) ends ----" + "\n";

            idContentString = idContentString + "\n" + "---- step 05 parse GPO and AFL ----";
            int gposSize = gpos.size();
            idContentString = idContentString + "\n" + "we do have " + gposSize + " gpos to process";
            for (int i = 0; i < gposSize; i++) {
                byte[] gpo = gpos.get(i);
                idContentString = idContentString + "\n" + "gpo: " + BytesUtils.bytesToString(gpo);
                byte[] extractedCardData = parser.extractCommonsCardData(gpo);
                if (extractedCardData != null) {
                    idContentString = idContentString + "\n" + TlvUtil.prettyPrintAPDUResponse(extractedCardData);
                }
                idContentString = idContentString + "\n" + "-------------------";
            }

            idContentString = idContentString + "\n" + "---- step 05 parse GPO and AFL ends ----" + "\n";


/*
            idContentString = idContentString + "\n" + "---- single task start ----";
            String[] typeAids;
            for (int i = 0; i < typeAids.length; i++) {
                idContentString = idContentString + "\n" + "step 2 select PID with AID";
                String aid = typeAids[i];
                idContentString = idContentString + "\n" + "aid " + i + " : " + aid;
                byte[] selectPidResponse = parser.selectPid(aid);
                // should return the PDOL
                idContentString = idContentString + "\n" + "selectPidResponse: " + BytesUtils.bytesToString(selectPidResponse);

            }
*/


            idContentString = idContentString + "\n" + "---- single task end ----" + "\n";
/*
            idContentString = idContentString + "\n" + "---- applications start ----";
            List<Application> applications = card.getApplications();
            for (int i = 0; i < applications.size(); i++) {
                Application application = applications.get(i);
                idContentString = idContentString + "\n" + "application: " + i;

                idContentString = idContentString + "\n" + "AID: " + BytesUtils.bytesToString(application.getAid());
                idContentString = idContentString + "\n" + "ApplicationLabel: " + application.getApplicationLabel();
                idContentString = idContentString + "\n" + "Left PIN try: " + application.getLeftPinTry();
                idContentString = idContentString + "\n" + "Priority: " + application.getPriority();
                idContentString = idContentString + "\n" + "Transaction counter: " + application.getTransactionCounter();
                idContentString = idContentString + "\n" + "Amount: " + application.getAmount();
                if (application.getListTransactions() != null) {
                    idContentString = idContentString + "\n" + "List transaction size: " + application.getListTransactions().size();
                }
                idContentString = idContentString + "\n" + "--- application end ---";
            }
            idContentString = idContentString + "\n" + "---- applications end ----";
            idContentString = idContentString + "\n" + "cardNumber: " + prettyPrintCardNumber(cardNumber);
            idContentString = idContentString + "\n" + "expireDate: " + expireDateFormat;
            idContentString = idContentString + "\n" + "at: " + card.getAt();

 */
            /*
            AtrUtils atrUtils = new AtrUtils(getApplicationContext());
            card.setAtrDescription(config.contactLess ? atrUtils.getDescriptionFromAts(card.getAt()) : atrUtils.getDescription(card.getAt()));
            idContentString = idContentString + "\n" + "at Description: " + card.getAtrDescription();
            */

            // get the complete analyzed data, analyse them after the data is read completely
            EmvCardAnalyze emvCardAnalyze = parser.getEmvCardAnalyze();
            byte[] apduSelectPpseCommand = emvCardAnalyze.getApduSelectPpseCommand();
            byte[] apduSelectPpseResponse = emvCardAnalyze.getApduSelectPpseResponse();
            idContentString = idContentString + "\n" + "apduSelectPpseCommand: " + BytesUtils.bytesToString(apduSelectPpseCommand);
            idContentString = idContentString + "\n" + "apduSelectPpseResponse: " + BytesUtils.bytesToString(apduSelectPpseResponse);

            /*
            byte[] apduGetProcessingOptionsCommand = emvCardAnalyze.getApduGetProcessingOptionsCommand();
            byte[] apduGetProcessingOptionsResponse = emvCardAnalyze.getApduGetProcessingOptionsResponse();

            idContentString = idContentString + "\n" + "apduGetProcessingOptionsCommand: " + BytesUtils.bytesToString(apduGetProcessingOptionsCommand);
            idContentString = idContentString + "\n" + "apduGetProcessingOptionsResponse: " + BytesUtils.bytesToString(apduGetProcessingOptionsResponse);
            byte[] applicationInterchangeProfileByte = TlvUtil.getValue(apduGetProcessingOptionsResponse, EmvTags.APPLICATION_INTERCHANGE_PROFILE);
            idContentString = idContentString + "\n" + "applicationInterchangeProfile: " + BytesUtils.bytesToString(applicationInterchangeProfileByte);
            String applicationInterchangeProfileBit = Utils.printByteArrayBinary(applicationInterchangeProfileByte);
            idContentString = idContentString + "\n" + "AIP: " + applicationInterchangeProfileBit;
            ApplicationInterchangeProfile applicationInterchangeProfile = new ApplicationInterchangeProfile(applicationInterchangeProfileByte[0], applicationInterchangeProfileByte[1]);
            idContentString = idContentString + "\n" + "AIP: " + applicationInterchangeProfile.toString();

            byte applicationInterchangeProfileFirstByte = applicationInterchangeProfileByte[0];
            idContentString = idContentString + "\n" + "applicationInterchangeProfileFirstByte: " + applicationInterchangeProfileFirstByte;
            boolean isCardholderVerificationSupported = applicationInterchangeProfile.isCardholderVerificationSupported();
            idContentString = idContentString + "\n" + "isCardholderVerificationSupported: " + isCardholderVerificationSupported;

             */

            // section for CPLC data starts
            // check in EmvTemplate.java for this line:
            // public boolean readCplc = true;
            // not all cards allow this
            /*
            idContentString = idContentString + "\n" + "---- CPLC DATA start ----";
            try {
                int icBatchId = card.getCplc().getIcBatchId();
                idContentString = idContentString + "\n" + " icBatchId: " + icBatchId;
                int iccManufacturer = card.getCplc().getIccManufacturer();
                idContentString = idContentString + "\n" + " iccManufacturer: " + iccManufacturer;
                Date icEmbeddingDate = card.getCplc().getIcEmbeddingDate();
                idContentString = idContentString + "\n" + " icEmbeddingDate: " + icEmbeddingDate;
                int icFabricator = card.getCplc().getIcFabricator();
                idContentString = idContentString + "\n" + " icFabricator: " + icFabricator;
                Date icFabricDate = card.getCplc().getIcFabricDate();
                idContentString = idContentString + "\n" + " icFabricDate: " + icFabricDate;
                int icModuleFabricator = card.getCplc().getIcModuleFabricator();
                idContentString = idContentString + "\n" + " icModuleFabricator: " + icModuleFabricator;
                Date icPackagingDate = card.getCplc().getIcPackagingDate();
                idContentString = idContentString + "\n" + " icPackagingDate: " + icPackagingDate;
                int icSerialNumber = card.getCplc().getIcSerialNumber();
                idContentString = idContentString + "\n" + " icSerialNumber: " + icSerialNumber;
                int icType = card.getCplc().getIcType();
                idContentString = idContentString + "\n" + " icType: " + icType;
                int icOs = card.getCplc().getOs();
                idContentString = idContentString + "\n" + " icOs: " + icOs;
                Date icOsReleaseDate = card.getCplc().getOsReleaseDate();
                idContentString = idContentString + "\n" + " icOsReleaseDate: " + icOsReleaseDate;
                int icReleaseLevel = card.getCplc().getOsReleaseLevel();
                idContentString = idContentString + "\n" + " icReleaseLevel: " + icReleaseLevel;
                Date icPersoDate = card.getCplc().getPersoDate();
                idContentString = idContentString + "\n" + " icPersoDate: " + icPersoDate;
                int icPersoEquipment = card.getCplc().getPersoEquipment();
                idContentString = idContentString + "\n" + " icPersoEquipment: " + icPersoEquipment;
                int icPreparesoId = card.getCplc().getPrepersoId();
                idContentString = idContentString + "\n" + " icPreparesoId: " + icPreparesoId;
            } catch (IllegalArgumentException e) {
                idContentString = idContentString + "\n" + " Exception: " + e;
            }
            idContentString = idContentString + "\n" + "---- CPLC DATA end ----";
             */
            // section for CPLC data ends
/*
            // section for servicecodes starts
            if (card.getTrack2().getService() != null) {
                ServiceCode3Enum serviceCode3Enum = card.getTrack2().getService().getServiceCode3();
                idContentString = idContentString + "\n" + " serviceCode3Enum: " + serviceCode3Enum.getAllowedServices() + " PIN: " + serviceCode3Enum.getPinRequirements();
            } else {
                idContentString = idContentString + "\n" + " serviceCode3Enum is not readable";
            }
            // section for servicecodes ends

            // cvm end
*/
            String finalIdContentString = idContentString;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //UI related things, not important for NFC
                    readResult.setText(finalIdContentString);
                }
            });
            try {
                isoDep.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            //Trying to catch any ioexception that may be thrown
            e.printStackTrace();
        } catch (Exception e) {
            //Trying to catch any exception that may be thrown
            e.printStackTrace();
        }

    }


    public static String prettyPrintCardNumber(String cardNumber) {
        if (cardNumber == null) return null;
        char delimiter = ' ';
        return cardNumber.replaceAll(".{4}(?!$)", "$0" + delimiter);
    }

    private void showWirelessSettings() {
        Toast.makeText(this, "You need to enable NFC", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        startActivity(intent);
    }


    private void writeToUiToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(getApplicationContext(),
                    message,
                    Toast.LENGTH_SHORT).show();
        });
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (mNfcAdapter != null) {
            Bundle options = new Bundle();
            // Work around for some broken Nfc firmware implementations that poll the card too fast
            options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);

            // Enable ReaderMode for all types of card and disable platform sounds
            // the option NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK is NOT set
            // to get the data of the tag after reading
            mNfcAdapter.enableReaderMode(this,
                    this,
                    NfcAdapter.FLAG_READER_NFC_A |
                            NfcAdapter.FLAG_READER_NFC_B |
                            NfcAdapter.FLAG_READER_NFC_F |
                            NfcAdapter.FLAG_READER_NFC_V |
                            NfcAdapter.FLAG_READER_NFC_BARCODE |
                            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    options);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableReaderMode(this);
    }

    // section for main menu

    private void exportDumpMail() {
        if (dumpExportString.isEmpty()) {
            writeToUiToast("Scan a tag first before sending emails :-)");
            return;
        }
        String subject = "Dump NFC-Tag " + tagTypeString + " UID: " + tagIdString;
        String body = dumpExportString;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    private void exportDumpFile() {
        if (dumpExportString.isEmpty()) {
            writeToUiToast("Scan a tag first before writing files :-)");
            return;
        }
        verifyPermissionsWriteString();
    }

    // section external storage permission check
    private void verifyPermissionsWriteString() {
        String[] permissions = {Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                permissions[0]) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this.getApplicationContext(),
                permissions[1]) == PackageManager.PERMISSION_GRANTED) {
            writeStringToExternalSharedStorage();
        } else {
            ActivityCompat.requestPermissions(this,
                    permissions,
                    REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE);
        }
    }

    private void writeStringToExternalSharedStorage() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Optionally, specify a URI for the file that should appear in the
        // system file picker when it loads.
        //boolean pickerInitialUri = false;
        //intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);
        // get filename from edittext
        String filename = tagTypeString + "_" + tagIdString + ".txt";
        // sanity check
        if (filename.equals("")) {
            writeToUiToast("scan a tag before writing the content to a file :-)");
            return;
        }
        intent.putExtra(Intent.EXTRA_TITLE, filename);
        fileSaverActivityResultLauncher.launch(intent);
    }

    ActivityResultLauncher<Intent> fileSaverActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        Intent resultData = result.getData();
                        // The result data contains a URI for the document or directory that
                        // the user selected.
                        Uri uri = null;
                        if (resultData != null) {
                            uri = resultData.getData();
                            // Perform operations on the document using its URI.
                            try {
                                // get file content from edittext
                                String fileContent = dumpExportString;
                                writeTextToUri(uri, fileContent);
                                String message = "file written to external shared storage: " + uri.toString();
                                writeToUiToast("file written to external shared storage: " + uri.toString());
                            } catch (IOException e) {
                                e.printStackTrace();
                                writeToUiToast("ERROR: " + e.toString());
                                return;
                            }
                        }
                    }
                }
            });

    private void writeTextToUri(Uri uri, String data) throws IOException {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(contextSave.getContentResolver().openOutputStream(uri));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
        } catch (IOException e) {
            System.out.println("Exception File write failed: " + e.toString());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_main, menu);

        MenuItem mExportMail = menu.findItem(R.id.action_export_mail);
        mExportMail.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                //Intent i = new Intent(MainActivity.this, AddEntryActivity.class);
                //startActivity(i);
                exportDumpMail();
                return false;
            }
        });

        MenuItem mExportFile = menu.findItem(R.id.action_export_file);
        mExportFile.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                //Intent i = new Intent(MainActivity.this, AddEntryActivity.class);
                //startActivity(i);
                exportDumpFile();
                return false;
            }
        });

        MenuItem mClearDump = menu.findItem(R.id.action_clear_dump);
        mClearDump.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                dumpExportString = "";
                readResult.setText("read result");
                return false;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }
}