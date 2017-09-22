//
//  SOSPicker.h
//  SyncOnSet
//
//  Created by Christopher Sullivan on 10/25/13.
//
//

#import <Cordova/CDVPlugin.h>
#import "ELCAlbumPickerController.h"
#import "ELCImagePickerController.h"

@interface SOSPicker : CDVPlugin <ELCImagePickerControllerDelegate, UINavigationControllerDelegate, UIScrollViewDelegate>

@property (copy)   NSString* callbackId;

- (void) getPictures:(CDVInvokedUrlCommand *)command;
- (UIImage*)imageByScalingNotCroppingForSize:(UIImage*)anImage toSize:(CGSize)frameSize;

@property (nonatomic, assign) BOOL useOriginal;
@property (nonatomic, assign) BOOL createThumbnail;
@property (nonatomic, assign) BOOL saveToDataDirectory;
@property (nonatomic, assign) NSInteger width;
@property (nonatomic, assign) NSInteger height;
@property (nonatomic, assign) NSInteger quality;
@property (nonatomic, assign) NSInteger total;
@property (nonatomic, assign) NSInteger exist;

@end
