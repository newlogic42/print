package org.idpass.lite;

import com.google.protobuf.ByteString;
import io.mosip.kernel.core.util.CryptoUtil;
import org.api.proto.Dat;
import org.api.proto.Ident;
import org.api.proto.KV;
import org.idpass.lite.exceptions.IDPassException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.github.jaiimageio.jpeg2000.impl.J2KImageReader;

/**
 * Spring boot singleton component execution wrapper of
 * an IDPassReader instance
 */

@Component
public class IDPassReaderComponent
{
    public IDPassReader reader;

    /**
     * Instantiates IDPassReader reader with a particular configuration
     *
     * @throws IDPassException Standard exception
     * @throws IOException Standard exception
     */
    public IDPassReaderComponent()
            throws IDPassException, IOException
    {
        reader = new IDPassReader();
        reader.setDetailsVisible(
                IDPassReader.DETAIL_GIVENNAME |
                IDPassReader.DETAIL_SURNAME |
                IDPassReader.DETAIL_PLACEOFBIRTH);
    }

    /**
     * Returns a PNG image QR code representation as a byte[] array,
     * from the given inputs:
     *
     * @param cs The credential subject input json
     * @param pincode The IDPASS LITE pin code
     * @param photob64 A facial photo image
     * @return Returns PNG QR code of the generated IDPASS LITE card
     */
    public byte[] generateQrCode(String cs, String pincode, String photob64)
    {
        // Parse credential subject input json to populate fields in IdentFields object
        IdentFields idf = IdentFields.getInstance(cs);

        Ident.Builder identBuilder = Ident.newBuilder()
                .setPin(pincode);

        String imageType = photob64.split(",")[0];
        byte[] photo = CryptoUtil.decodeBase64(photob64.split(",")[1]);
        photo = convertJ2KToJPG(photo);

        if (photo != null) {
            identBuilder.setPhoto(ByteString.copyFrom(photo));
        }

        /*
        UIN
        gender
        givenName
        surName
        placeOfBirth
        dateOfBirth
        address
        */

        /* Populate Ident fields from idf object */

        String dobStr = idf.getDateOfBirth();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/d");
        LocalDate dob = LocalDate.parse(dobStr, formatter);
        Dat dobProto = Dat.newBuilder()
                .setYear(dob.getYear())
                .setMonth(dob.getMonthValue())
                .setDay(dob.getDayOfMonth())
                .build();

        identBuilder.addPrivExtra(KV.newBuilder().setKey("UIN").setValue(idf.getUIN()));
        identBuilder.addPubExtra(KV.newBuilder().setKey("Gender").setValue(idf.getGender()));
        identBuilder.addPubExtra(KV.newBuilder().setKey("Address").setValue(idf.getAddress()));
        identBuilder.setGivenName(idf.getGivenName());
        identBuilder.setSurName(idf.getSurName());
        identBuilder.setPlaceOfBirth(idf.getPlaceOfBirth());
        identBuilder.setDateOfBirth(dobProto);

        Ident ident = identBuilder.build();
        byte[] qrcodeId = null;

        try {
            Card card = reader.newCard(ident, null);
            BufferedImage bi = card.asQRCode();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(bi, "png", bos);
            qrcodeId = bos.toByteArray();

        } catch (IOException | IDPassException e) {
            e.printStackTrace();
        }

        return  qrcodeId;
    }

    // Notes: copied from 'mosip-functional-tests' repo
    private static byte[] convertJ2KToJPG(byte[] jp2Data) {
        byte[] jpgImg = null;
        J2KImageReader j2kImageReader = new J2KImageReader(null);
        try {
            j2kImageReader.setInput(ImageIO.createImageInputStream(new ByteArrayInputStream(jp2Data)));
            ImageReadParam imageReadParam = j2kImageReader.getDefaultReadParam();
            BufferedImage image = j2kImageReader.read(0, imageReadParam);
            ByteArrayOutputStream imgBytes = new ByteArrayOutputStream();
            ImageIO.write(image, "JPG", imgBytes);
            jpgImg = imgBytes.toByteArray();
        } catch (IOException e) {

        }

        return jpgImg;
    }
}
