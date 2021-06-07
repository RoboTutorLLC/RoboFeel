import numpy as np
import cv2
from PIL import Image
import base64
import io
from skimage.io import imread, imshow
from skimage.transform import resize
from skimage.feature import hog
from skimage import exposure
import matplotlib.pyplot as plt
from skimage.util import img_as_ubyte
from skimage.color import gray2rgb
import re


#%matplotlib inline

def main(data, boxesList):
    decoded_data = base64.b64decode(data)
    np_data =  np.fromstring(decoded_data,np.uint8)
    img = cv2.imdecode(np_data,cv2.IMREAD_UNCHANGED)
    img_gray = img.copy()
    #img_gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    #resized_img = resize(img, (512,256)) 

    currstr = boxesList.get(0)
    split_box = re.split(':|,',currstr)

    rectangle_coords = [int(split_box[1])-10,int(split_box[3])-10,int(split_box[5])+10,int(split_box[7])+10]
    if rectangle_coords[0]<0:
        rectangle_coords[2]=min(img_gray.shape[0],rectangle_coords[2]+rectangle_coords[0])
        rectangle_coords[0]=0
    if(rectangle_coords[1]<0):
        rectangle_coords[3]=min(img_gray.shape[1],  rectangle_coords[3]+rectangle_coords[1])
        rectangle_coords[1]=0

    img_gray = cv2.rectangle(img_gray,(rectangle_coords[0],rectangle_coords[1]),(rectangle_coords[2],rectangle_coords[3]),(255,0,0))

    img_gray = img_gray[rectangle_coords[1]:rectangle_coords[3]+1,rectangle_coords[0]:rectangle_coords[2]+1]
    
    img_gray = resize(img_gray,(384,192))


    fd, hog_image = hog(img_gray, orientations=9, pixels_per_cell=(8, 8), 
                    cells_per_block=(2, 2), visualize=True, multichannel=True)
  
    hog_image_rescaled = exposure.rescale_intensity(hog_image, in_range=(0, 0.01)) 
    hog_image_rescaled_opencv = img_as_ubyte(hog_image_rescaled)

    
    pil_img = Image.fromarray(hog_image_rescaled_opencv)
    buff = io.BytesIO()
    pil_img.save(buff,format = "PNG")
    img_str = base64.b64encode(buff.getvalue())

    list_of_descriptors = fd.tolist()

    return ""+str(img_str, 'utf-8'), list_of_descriptors
  

