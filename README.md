sz-cordova-imagePicker
=======================

```
This is a fork of https://github.com/wymsee/cordova-imagePicker with these 
added enhancements.

Added: 

    - Select all button (both iOS and Android)
    - Selected images counter (both iOS and Android)
    - (bool) options.useOriginal can be used to gain a huge performance boost
      when you don't need to do any image manipulation (both iOS and Android)
    - (bool) options.createThumbnail to create thumbnails with name begins with
      "thumb_" (both iOS and Android)
    - (bool) options.saveToDataDirectory lets you save selected files to cordova
      .file.dataDirectory (Library/NoCloud on iOS) and (<app-id>/files on Android)
      instead of temporary directory. (both iOS and Android)
    - Thai Locale (both iOS and Android)
    - Loading spinner (iOS)
    - Adaptive layout that supports iPhone 6, 6+ and possibly future models (iOS)
    - Cosmetic changes reflecting iOS 8 (iOS)
    - Fixed crash when filename is shorter than three characters (Android)
    - Fixed crash when resumed after taking a new photo (Android)

```

You should read the document from the upstream about how to use the plugin.

## Installing the plugin

The plugin conforms to the Cordova plugin specification, it can be installed
using the Cordova / Phonegap command line interface.

    phonegap plugin add https://github.com/pawee/snw-cordova-imagePicker.git

    cordova plugin add https://github.com/pawee/snw-cordova-imagePicker.git

## Libraries used

#### ELCImagePicker

For iOS this plugin uses the ELCImagePickerController, with slight modifications for the iOS image picker.  ELCImagePicker uses the MIT License which can be found in the file LICENSE.

https://github.com/B-Sides/ELCImagePickerController

#### MultiImageChooser

For Android this plugin uses MultiImageChooser, with modifications.  MultiImageChooser uses the BSD 2-Clause License which can be found in the file BSD_LICENSE.  Some code inside MultImageChooser is licensed under the Apache license which can be found in the file APACHE_LICENSE.

https://github.com/derosa/MultiImageChooser

#### FakeR

Code(FakeR) was also taken from the phonegap BarCodeScanner plugin.  This code uses the MIT license.

https://github.com/wildabeast/BarcodeScanner

## License

The MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
