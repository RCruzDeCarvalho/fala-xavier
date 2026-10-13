package pt.ricardo.falaxavier;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Dá à câmara um sítio onde gravar a foto (um único ficheiro na cache da app). */
public class FotoProvider extends ContentProvider {

    private static final String FOTO = "foto.jpg";

    @Override
    public boolean onCreate() {
        return true;
    }

    private File ficheiro() {
        return new File(getContext().getCacheDir(), FOTO);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!FOTO.equals(uri.getLastPathSegment())) throw new FileNotFoundException();
        int m;
        if (mode.contains("w")) {
            m = ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                    | (mode.contains("r") ? ParcelFileDescriptor.MODE_READ_WRITE
                                          : ParcelFileDescriptor.MODE_WRITE_ONLY);
        } else {
            m = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        return ParcelFileDescriptor.open(ficheiro(), m);
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        File f = ficheiro();
        c.addRow(new Object[]{FOTO, f.exists() ? f.length() : 0L});
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] args) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
