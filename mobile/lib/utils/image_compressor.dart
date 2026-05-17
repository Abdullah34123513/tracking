import 'dart:io';
import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:path_provider/path_provider.dart';

class ImageCompressor {
  /// Compress a screenshot file to WebP format with quality reduction.
  /// This drastically reduces file size for upload.
  static Future<File?> compressScreenshot(File sourceFile, {int quality = 40}) async {
    try {
      final dir = await getTemporaryDirectory();
      final targetPath = '${dir.path}/compressed_${DateTime.now().millisecondsSinceEpoch}.webp';

      final result = await FlutterImageCompress.compressAndGetFile(
        sourceFile.absolute.path,
        targetPath,
        format: CompressFormat.webp,
        quality: quality,
        minWidth: 720,  // Reduce resolution for bandwidth savings
        minHeight: 1280,
      );

      if (result != null) {
        return File(result.path);
      }
      return null;
    } catch (e) {
      print('Compression error: $e');
      return sourceFile; // Return original if compression fails
    }
  }

  /// Compress multiple screenshots (for post-call batch)
  static Future<List<File>> compressBatch(List<File> files, {int quality = 35}) async {
    final compressed = <File>[];
    for (final file in files) {
      final result = await compressScreenshot(file, quality: quality);
      if (result != null) {
        compressed.add(result);
      }
    }
    return compressed;
  }
}
