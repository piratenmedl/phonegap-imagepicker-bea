//
//  ELCAssetTablePicker.h
//
//  Created by ELC on 2/15/11.
//  Copyright 2011 ELC Technologies. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <AssetsLibrary/AssetsLibrary.h>
#import "ELCAsset.h"
#import "ELCAssetSelectionDelegate.h"
#import "ELCAssetPickerFilterDelegate.h"

@interface ELCAssetTablePicker : UITableViewController <ELCAssetDelegate>

@property (nonatomic, weak) id <ELCAssetSelectionDelegate> parent;
@property (nonatomic, strong) ALAssetsGroup *assetGroup;
@property (nonatomic, strong) NSMutableArray *elcAssets;
@property (nonatomic, strong) IBOutlet UILabel *selectedAssetsLabel;
@property (nonatomic, assign) BOOL singleSelection;
@property (nonatomic, assign) BOOL immediateReturn;
@property (nonatomic, weak) UIActivityIndicatorView *activityIndicator;
@property (nonatomic, strong) UIBarButtonItem *selectAllButton;
@property (nonatomic, strong) UIBarButtonItem *deselectAllButton;
@property (nonatomic, strong) UIBarButtonItem *flexSpace;
@property (nonatomic, strong) UIBarButtonItem *selectionCounterButton;
@property (nonatomic, strong) UILabel *selectionCounter;
@property (nonatomic, strong) UIBarButtonItem *doneButton; //bea
@property (nonatomic, assign) NSInteger total;
@property (nonatomic, assign) NSInteger vorh;

// optional, can be used to filter the assets displayed
@property(nonatomic, weak) id<ELCAssetPickerFilterDelegate> assetPickerFilterDelegate;

- (int)totalSelectedAssets;
- (void)preparePhotos;
- (void)updateCounter;

- (void)doneAction:(id)sender;
- (void)selectAllAction:(id)sender;
- (void)deselectAllAction:(id)sender;

@end