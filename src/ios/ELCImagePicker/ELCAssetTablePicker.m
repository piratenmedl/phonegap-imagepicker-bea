//
//  ELCAssetTablePicker.m
//
//  Created by ELC on 2/15/11.
//  Copyright 2011 ELC Technologies. All rights reserved.
//

#import "ELCAssetTablePicker.h"
#import "ELCAssetCell.h"
#import "ELCAsset.h"
#import "ELCAlbumPickerController.h"

@interface ELCAssetTablePicker ()

@property (nonatomic, assign) int columns;
@property (nonatomic, assign) CGFloat assetDimension;
@property (nonatomic, assign) int assetPadding;

@end

@implementation ELCAssetTablePicker

//Using auto synthesizers

- (id)init
{
    self = [super init];
    if (self) {
        //Sets a reasonable default bigger then 0 for columns
        //So that we don't have a divide by 0 scenario
        self.columns = 4;
        self.assetPadding = 2;
    }
    return self;
}

- (void)viewDidLoad
{
    [super viewDidLoad];
    
    [self.tableView setSeparatorStyle:UITableViewCellSeparatorStyleNone];
	[self.tableView setAllowsSelection:NO];

    NSMutableArray *tempArray = [[NSMutableArray alloc] init];
    self.elcAssets = tempArray;
	
    if (self.immediateReturn) {
        
    } else {
        UIBarButtonItem *doneButtonItem = [[UIBarButtonItem alloc]
                                           //initWithBarButtonSystemItem:UIBarButtonSystemItemDone
                                initWithTitle: NSLocalizedString(@"Done", nil)
                                style:UIBarButtonItemStylePlain
                                           target:self
                                           action:@selector(doneAction:)];        
        [self.navigationItem setRightBarButtonItem:doneButtonItem];
        [self.navigationItem setTitle:NSLocalizedString(@"Loading", nil)];

        [self.navigationController setToolbarHidden:NO];
        self.flexSpace = [[UIBarButtonItem alloc] initWithBarButtonSystemItem:UIBarButtonSystemItemFlexibleSpace
                                                                       target:nil
                                                                       action:nil];
        self.selectionCounter = [[UILabel alloc] initWithFrame:CGRectMake(0, 0, 100, self.navigationController.toolbar.frame.size.height)];
        [self.selectionCounter setTextAlignment:NSTextAlignmentRight];
        [self.selectionCounter setTextColor:[UIColor grayColor]];
        [self updateCounter];
        
        UIButton *button =  [UIButton buttonWithType:UIButtonTypeSystem];
        [button setFrame:CGRectMake(0, 0, 100, self.navigationController.toolbar.frame.size.height)];
        [button addSubview:self.selectionCounter];
        self.selectionCounterButton = [[UIBarButtonItem alloc]
                                           initWithCustomView:button];
        [self.selectionCounterButton setEnabled:true];
        
        
        self.selectAllButton = [[UIBarButtonItem alloc]
                                initWithTitle: NSLocalizedString(@"Select All", nil)
                                style:UIBarButtonItemStylePlain
                                target:self
                                action:@selector(selectAllAction:)];
        self.deselectAllButton = [[UIBarButtonItem alloc]
                                  initWithTitle: NSLocalizedString(@"Deselect All", nil)
                                  style:UIBarButtonItemStylePlain
                                  target:self
                                  action:@selector(deselectAllAction:)];
        [self deselectAllAction:nil];
    }

	[self performSelectorInBackground:@selector(preparePhotos) withObject:nil];
}

- (void)loadView {
    [super loadView];
    UIActivityIndicatorView *activityIndicator = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:UIActivityIndicatorViewStyleWhiteLarge];
    activityIndicator.frame = CGRectMake(0, 0, 80, 80);
    activityIndicator.backgroundColor = [UIColor colorWithRed:0 green:0 blue:0 alpha:0.8];
    activityIndicator.layer.cornerRadius = 10;
    activityIndicator.hidesWhenStopped = YES;
    [self.view addSubview:activityIndicator];
    self.activityIndicator = activityIndicator;
}

- (void)viewWillLayoutSubviews {
    [super viewWillLayoutSubviews];
    CGRect viewBounds = self.view.bounds;
    self.activityIndicator.center = CGPointMake(CGRectGetMidX(viewBounds), CGRectGetMidY(viewBounds));
}

- (void)viewWillAppear:(BOOL)animated
{
    [super viewWillAppear:animated];
    //self.columns = self.view.bounds.size.width / 80;
    self.columns = 4;
    self.assetPadding = 2;
    [self recalculateAssetDimension];
}

- (BOOL)shouldAutorotateToInterfaceOrientation:(UIInterfaceOrientation)toInterfaceOrientation
{
    return YES;
}

- (void)didRotateFromInterfaceOrientation:(UIInterfaceOrientation)fromInterfaceOrientation
{
    [super didRotateFromInterfaceOrientation:fromInterfaceOrientation];
    //self.columns = self.view.bounds.size.width / 80;
    self.columns = 4;
    self.assetPadding = 2;
    [self recalculateAssetDimension];
    [self.tableView reloadData];    
}

- (void)recalculateAssetDimension // Modded
{
    self.assetDimension = (self.view.bounds.size.width - ((self.columns - 1) * self.assetPadding) - (self.assetPadding*2)) / self.columns;
}

- (void)preparePhotos
{
    @autoreleasepool {

        [self.assetGroup enumerateAssetsUsingBlock:^(ALAsset *result, NSUInteger index, BOOL *stop) {
            
            if (result == nil) {
                return;
            }

            ELCAsset *elcAsset = [[ELCAsset alloc] initWithAsset:result];
            [elcAsset setParent:self];
            
            BOOL isAssetFiltered = NO;
            if (self.assetPickerFilterDelegate &&
               [self.assetPickerFilterDelegate respondsToSelector:@selector(assetTablePicker:isAssetFilteredOut:)])
            {
                isAssetFiltered = [self.assetPickerFilterDelegate assetTablePicker:self isAssetFilteredOut:(ELCAsset*)elcAsset];
            }

            if (!isAssetFiltered) {
                [self.elcAssets addObject:elcAsset];
            }

         }];

        dispatch_sync(dispatch_get_main_queue(), ^{
            [self.tableView reloadData];
            // scroll to bottom
            long section = [self numberOfSectionsInTableView:self.tableView] - 1;
            long row = [self tableView:self.tableView numberOfRowsInSection:section] - 1;
            if (section >= 0 && row >= 0) {
                NSIndexPath *ip = [NSIndexPath indexPathForRow:row
                                                     inSection:section];
                        [self.tableView scrollToRowAtIndexPath:ip
                                              atScrollPosition:UITableViewScrollPositionBottom
                                                      animated:NO];
            }
            
            [self.navigationItem setTitle:self.singleSelection ? NSLocalizedString(@"Pick Photo", nil) : NSLocalizedString(@"Pick Photos", nil)];
        });
    }
}

- (void)updateCounter
{
    //bea
	if (self.vorhImages) 
		NSInteger fotos = self.vorhImages-self.totalSelectedAssets;
		[self.selectionCounter setText:[NSString stringWithFormat:NSLocalizedString(@"Chosen: %d", nil), fotos]];
	else
		[self.selectionCounter setText:[NSString stringWithFormat:NSLocalizedString(@"Chosen: %d", nil), self.totalSelectedAssets]];
		//[self.selectionCounter setText:[NSString stringWithFormat:NSLocalizedString(@"Chosen: %d", nil), self.totalImages]];
}

- (void)selectAllAction:(id)sender
{
    for (ELCAsset *asset in [self.elcAssets reverseObjectEnumerator]) {
        if([self shouldSelectAsset:asset]) {
            asset.selected = YES;
        } else {
            break;
        }
    }
    
    for (NSInteger j = 0; j < [self.tableView numberOfSections]; ++j)
    {
        for (NSInteger i = 0; i < [self.tableView numberOfRowsInSection:j]; ++i)
        {
            ELCAssetCell *cell = (ELCAssetCell*)[self.tableView cellForRowAtIndexPath:[NSIndexPath indexPathForRow:i inSection:j]];
            [cell toggleOverlays];
        }
    }

    [self setToolbarItems:[NSArray arrayWithObjects:self.deselectAllButton, self.flexSpace, self.selectionCounterButton, nil]];
}

- (void)deselectAllAction:(id)sender
{
    for (ELCAsset *asset in self.elcAssets) {
        asset.selected = NO;
    }
    
    for (NSInteger j = 0; j < [self.tableView numberOfSections]; ++j)
    {
        for (NSInteger i = 0; i < [self.tableView numberOfRowsInSection:j]; ++i)
        {
            ELCAssetCell *cell = (ELCAssetCell*)[self.tableView cellForRowAtIndexPath:[NSIndexPath indexPathForRow:i inSection:j]];
            [cell toggleOverlays];
        }
    }

    [self setToolbarItems:[NSArray arrayWithObjects:self.selectAllButton, self.flexSpace, self.selectionCounterButton, nil]];
}

- (void)doneAction:(id)sender
{
    [self.activityIndicator startAnimating];
    [self performSelectorInBackground:@selector(prepareAssets) withObject:nil];
}

- (void) prepareAssets {
    NSMutableArray *selectedAssetsImages = [[NSMutableArray alloc] init];
    
    for (ELCAsset *elcAsset in self.elcAssets) {
        if ([elcAsset selected]) {
            [selectedAssetsImages addObject:[elcAsset asset]];
        }
    }
    [self performSelectorOnMainThread:@selector(returnAssets:) withObject:selectedAssetsImages waitUntilDone:YES];
    [self.activityIndicator removeFromSuperview];
}

- (void) returnAssets:(NSMutableArray *)selectedAssetsImages {
      [self.parent selectedAssets:selectedAssetsImages];
}


- (BOOL)shouldSelectAsset:(ELCAsset *)asset
{
    NSUInteger selectionCount = 0;
    for (ELCAsset *elcAsset in self.elcAssets) {
        if (elcAsset.selected) selectionCount++;
    }
    BOOL shouldSelect = YES;
    if ([self.parent respondsToSelector:@selector(shouldSelectAsset:previousCount:)]) {
        shouldSelect = [self.parent shouldSelectAsset:asset previousCount:selectionCount];
    }
    return shouldSelect;
}

- (void)assetSelected:(ELCAsset *)asset
{
    if (self.singleSelection) {

        for (ELCAsset *elcAsset in self.elcAssets) {
            if (asset != elcAsset) {
                elcAsset.selected = NO;
            }
        }
    }
    if (self.immediateReturn) {
        NSArray *singleAssetArray = @[asset.asset];
        [(NSObject *)self.parent performSelector:@selector(selectedAssets:) withObject:singleAssetArray afterDelay:0];
    }
}

#pragma mark UITableViewDataSource Delegate Methods

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    // Return the number of sections.
    return 1;
}


- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    if (self.columns <= 0) { //Sometimes called before we know how many columns we have
        self.columns = 4;
    }
    NSInteger numRows = ceil([self.elcAssets count] / (float)self.columns);
    return numRows;
}

- (NSArray *)assetsForIndexPath:(NSIndexPath *)path
{
    long index = path.row * self.columns;
    long length = MIN(self.columns, [self.elcAssets count] - index);
    return [self.elcAssets subarrayWithRange:NSMakeRange(index, length)];
}

// Customize the appearance of table view cells.
- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{    
    static NSString *CellIdentifier = @"Cell";
        
    ELCAssetCell *cell = (ELCAssetCell*)[tableView dequeueReusableCellWithIdentifier:CellIdentifier];

    if (cell == nil) {		        
        cell = [[ELCAssetCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:CellIdentifier];
    }
    
    [cell setAssets:[self assetsForIndexPath:indexPath] withDimension:self.assetDimension withPadding:self.assetPadding];
    cell.parent = self;
    return cell;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath
{
	return (int)(self.assetDimension + self.assetPadding);
}

- (int)totalSelectedAssets
{
    int count = 0;
    
    for (ELCAsset *asset in self.elcAssets) {
		if (asset.selected) {
            count++;	
		}
	}
    
    return count;
}


@end
