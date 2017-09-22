//
//  SOSPicker.m
//  SyncOnSet
//
//  Created by Christopher Sullivan on 10/25/13.
//
//

#import "SOSPicker.h"
#import "ELCAlbumPickerController.h"
#import "ELCImagePickerController.h"
#import "ELCAssetTablePicker.h"

#define CDV_PHOTO_PREFIX @"snw_photo_"

@implementation SOSPicker

@synthesize callbackId;

- (void) getPictures:(CDVInvokedUrlCommand *)command {
	NSDictionary *options = [command.arguments objectAtIndex: 0];
    NSInteger maximumImagesCount = [[options objectForKey:@"maximumImagesCount"] integerValue];
    NSInteger total = [[options objectForKey:@"total"] integerValue];
    NSInteger vorh = [[options objectForKey:@"vorh"] integerValue];
    self.useOriginal = [[options objectForKey:@"useOriginal"] boolValue];
    self.createThumbnail = [[options objectForKey:@"createThumbnail"] boolValue];
    self.saveToDataDirectory = [[options objectForKey:@"saveToDataDirectory"] boolValue];
    self.width = [[options objectForKey:@"width"] integerValue];
    self.height = [[options objectForKey:@"height"] integerValue];
    self.quality = [[options objectForKey:@"quality"] integerValue];
    //self.total = [[options objectForKey:@"total"] integerValue];
    
    // Create the an album controller and image picker
    ELCAlbumPickerController *albumController = [[ELCAlbumPickerController alloc] init];
    
    if (maximumImagesCount == 1) {
        albumController.immediateReturn = true;
        albumController.singleSelection = true;
    } else {
        albumController.immediateReturn = false;
        albumController.singleSelection = false;
    }
    
    ELCImagePickerController *imagePicker = [[ELCImagePickerController alloc] initWithRootViewController:albumController];
    imagePicker.maximumImagesCount = maximumImagesCount;
    imagePicker.exist = vorh;
    imagePicker.total = total;
    imagePicker.returnsOriginalImage = 1;
    imagePicker.imagePickerDelegate = self;
    
    albumController.parent = imagePicker;
    self.callbackId = command.callbackId;
    // Present modally
    [self.viewController presentViewController:imagePicker
                                      animated:YES
                                    completion:nil];
    // You can run the plugin in background to avoid Xcode warning by putting above code inside the execution block below.
    // But there seems to have a problem with navigation bullet being delayed to appear.
    // [self.commandDelegate runInBackground:^{
    // }];
}


- (void)elcImagePickerController:(ELCImagePickerController *)picker didFinishPickingMediaWithInfo:(NSArray *)info {
    CDVPluginResult* result = nil;
    NSMutableArray *resultStrings = [[NSMutableArray alloc] init];
    Byte *buffer = 0;
    NSUInteger buffered = 0;
    NSData* data = nil;
    NSData* thumbData = nil;
    NSString *docsPath = [NSTemporaryDirectory()stringByStandardizingPath];
    if (self.saveToDataDirectory) {
        NSString *libPath = [NSSearchPathForDirectoriesInDomains(NSLibraryDirectory, NSUserDomainMask, YES) objectAtIndex:0];
        docsPath = [libPath stringByAppendingPathComponent:@"NoCloud"];
    }
    NSError* err = nil;
    NSFileManager* fileMgr = [[NSFileManager alloc] init];
    NSString* filePath;
    NSString* thumbPath;
    int fileName = 1;
    NSString *fileExtension = @"jpg";
    ALAsset* asset = nil;
    UIImageOrientation orientation = UIImageOrientationUp;;
    CGSize targetSize = CGSizeMake(self.width, self.height);
    for (NSDictionary *dict in info) {
        asset = [dict objectForKey:@"ALAsset"];
        // From ELCImagePickerController.m
        
        @autoreleasepool {
            ALAssetRepresentation *assetRep = [asset defaultRepresentation];
            CGImageRef imgRef = NULL;
            
            if(self.useOriginal) {
                
                buffer = (Byte*)malloc(assetRep.size);
                buffered = [assetRep getBytes:buffer fromOffset:0 length:assetRep.size error:nil];
                data = [NSData dataWithBytesNoCopy:buffer length:buffered freeWhenDone:YES];
                
                if ([assetRep.UTI isEqualToString:@"public.png"]) {
                    fileExtension = @"png";
                } else if([assetRep.UTI isEqualToString:@"public.jpg"]) {
                    fileExtension = @"jpg";
                }
                
            } else {
                //defaultRepresentation returns image as it appears in photo picker, rotated and sized,
                //so use UIImageOrientationUp when creating our image below.
                if (picker.returnsOriginalImage) {
                    imgRef = [assetRep fullResolutionImage];
                    
                    NSNumber *orientationValue = [asset valueForProperty:@"ALAssetPropertyOrientation"];
                    if (orientationValue != nil) {
                        orientation = [orientationValue intValue];
                    }
                } else {
                    imgRef = [assetRep fullScreenImage];
                }
                
                UIImage* image = [UIImage imageWithCGImage:imgRef scale:1.0f orientation:orientation];
                if (self.width == 0 && self.height == 0) {
                    data = UIImageJPEGRepresentation(image, self.quality/100.0f);
                } else {
                    UIImage* scaledImage = [self imageByScalingNotCroppingForSize:image toSize:targetSize];
                    data = UIImageJPEGRepresentation(scaledImage, self.quality/100.0f);
                }
                
                fileExtension = @"jpg";
                
            }
            
            do {
                filePath = [NSString stringWithFormat:@"%@/%@%04d.%@", docsPath, CDV_PHOTO_PREFIX, fileName, fileExtension];
                thumbPath = [NSString stringWithFormat:@"%@/thumb_%@%04d.%@", docsPath, CDV_PHOTO_PREFIX, fileName, fileExtension];
                fileName++;
            } while ([fileMgr fileExistsAtPath:filePath]);
            
            if (![data writeToFile:filePath options:NSAtomicWrite error:&err]) {
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_IO_EXCEPTION messageAsString:[err localizedDescription]];
                break;
            } else {

                if (self.createThumbnail) {
                    
                    imgRef = [asset thumbnail];
                    NSNumber *orientationValue = [asset valueForProperty:@"ALAssetPropertyOrientation"];
                    if (orientationValue != nil) {
                        orientation = [orientationValue intValue];
                    }
                    if([fileExtension isEqualToString:@"jpg"]) {
                        UIImage* image = [UIImage imageWithCGImage:imgRef scale:1.0f orientation:orientation];
                        thumbData = UIImageJPEGRepresentation(image, 75.0f/100.0f);
                    } else if([fileExtension isEqualToString:@"png"]) {
                        UIImage* image = [UIImage imageWithCGImage:imgRef scale:1.0f orientation:orientation];
                        thumbData = UIImagePNGRepresentation(image);
                    }
                    
                    [thumbData writeToFile:thumbPath options:NSAtomicWrite error:&err];
                    
                }

                [resultStrings addObject:[[NSURL fileURLWithPath:filePath] absoluteString]];
            }
        }
        
    }
    
    if (nil == result) {
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:resultStrings];
    }
    
    [self.viewController dismissViewControllerAnimated:YES completion:nil];
    [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
}

- (void)elcImagePickerControllerDidCancel:(ELCImagePickerController *)picker {
	[self.viewController dismissViewControllerAnimated:YES completion:nil];
	CDVPluginResult* pluginResult = nil;
    NSArray* emptyArray = [NSArray array];
	pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:emptyArray];
	[self.commandDelegate sendPluginResult:pluginResult callbackId:self.callbackId];
}

- (UIImage*)imageByScalingNotCroppingForSize:(UIImage*)anImage toSize:(CGSize)frameSize
{
    UIImage* sourceImage = anImage;
    UIImage* newImage = nil;
    CGSize imageSize = sourceImage.size;
    CGFloat width = imageSize.width;
    CGFloat height = imageSize.height;
    CGFloat targetWidth = frameSize.width;
    CGFloat targetHeight = frameSize.height;
    CGFloat scaleFactor = 0.0;
    CGSize scaledSize = frameSize;

    if (CGSizeEqualToSize(imageSize, frameSize) == NO) {
        CGFloat widthFactor = targetWidth / width;
        CGFloat heightFactor = targetHeight / height;

        // opposite comparison to imageByScalingAndCroppingForSize in order to contain the image within the given bounds
        if (widthFactor == 0.0) {
            scaleFactor = heightFactor;
        } else if (heightFactor == 0.0) {
            scaleFactor = widthFactor;
        } else if (widthFactor > heightFactor) {
            scaleFactor = heightFactor; // scale to fit height
        } else {
            scaleFactor = widthFactor; // scale to fit width
        }
        scaledSize = CGSizeMake(width * scaleFactor, height * scaleFactor);
    }

    UIGraphicsBeginImageContext(scaledSize); // this will resize

    [sourceImage drawInRect:CGRectMake(0, 0, scaledSize.width, scaledSize.height)];

    newImage = UIGraphicsGetImageFromCurrentImageContext();
    if (newImage == nil) {
        NSLog(@"could not scale image");
    }

    // pop the context to get back to the default
    UIGraphicsEndImageContext();
    return newImage;
}

@end
