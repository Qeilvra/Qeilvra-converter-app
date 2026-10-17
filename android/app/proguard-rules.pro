# PDFBox-Android exposes optional Gemalto JPEG-2000 encode/decode hooks, but does
# not ship that proprietary codec. FileFlow preserves existing PDF image streams
# and does not invoke JPX encoding, so the absent optional classes are safe here.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
